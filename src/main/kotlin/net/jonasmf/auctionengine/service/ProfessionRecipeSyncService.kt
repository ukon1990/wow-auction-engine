package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.integration.blizzard.ModifiedCraftingApiClient
import net.jonasmf.auctionengine.integration.blizzard.ProfessionApiClient
import net.jonasmf.auctionengine.integration.blizzard.RecipeApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

private const val PROFESSION_RECIPE_FETCH_CONCURRENCY = 20

private data class RecipeFetchOutcome(
    val recipeId: Int,
    val recipe: Recipe? = null,
    val error: Throwable? = null,
)

data class ProfessionRecipeSyncResult(
    val region: Region,
    val professionsFetched: Int,
    val skillTiersFetched: Int,
    val recipeReferencesDiscovered: Int,
    val recipesFetched: Int,
    val recipeFailures: Int,
    val modifiedCraftingCategoriesFetched: Int,
    val modifiedCraftingSlotsFetched: Int,
    val persistenceSummary: ProfessionRecipePersistenceSummary,
    val durationMs: Long,
)

@Service
class ProfessionRecipeSyncService(
    private val properties: BlizzardApiProperties,
    private val professionApiClient: ProfessionApiClient,
    private val recipeApiClient: RecipeApiClient,
    private val modifiedCraftingApiClient: ModifiedCraftingApiClient,
    private val professionRecipeBulkSyncService: ProfessionRecipeBulkSyncService,
) {
    private val log = LoggerFactory.getLogger(ProfessionRecipeSyncService::class.java)

    fun syncAllConfiguredRegions(): List<ProfessionRecipeSyncResult> = listOf(syncConfiguredStaticDataRegion())

    fun syncConfiguredStaticDataRegion(): ProfessionRecipeSyncResult = syncRegion(properties.staticDataRegion)

    fun syncRegion(region: Region): ProfessionRecipeSyncResult {
        val startTime = System.currentTimeMillis()
        log.info("Starting profession/recipe sync for region {}", region)

        val modifiedCraftingFetchStartTime = System.currentTimeMillis()
        log.info("Fetching modified crafting categories for region {}", region)
        val modifiedCraftingCategories = modifiedCraftingApiClient.getAllCategories(region)
        log.info(
            "Fetched modified crafting categories for region {} count={} in {}ms",
            region,
            modifiedCraftingCategories.size,
            System.currentTimeMillis() - modifiedCraftingFetchStartTime,
        )
        val modifiedCraftingSlotFetchStartTime = System.currentTimeMillis()
        log.info("Fetching modified crafting slot types for region {}", region)
        val modifiedCraftingSlots = modifiedCraftingApiClient.getAllSlotTypes(region)
        log.info(
            "Fetched modified crafting slot types for region {} count={} in {}ms",
            region,
            modifiedCraftingSlots.size,
            System.currentTimeMillis() - modifiedCraftingSlotFetchStartTime,
        )
        val metadataPersistStartTime = System.currentTimeMillis()
        professionRecipeBulkSyncService.syncModifiedCraftingMetadata(modifiedCraftingCategories, modifiedCraftingSlots)
        log.info(
            "Persisted modified crafting metadata for region {} categories={} slots={} in {}ms",
            region,
            modifiedCraftingCategories.size,
            modifiedCraftingSlots.size,
            System.currentTimeMillis() - metadataPersistStartTime,
        )

        val professionFetchStartTime = System.currentTimeMillis()
        val professions = professionApiClient.getAll(region)
        log.info(
            "Fetched professions for region {} count={} tiers={} in {}ms",
            region,
            professions.size,
            professions.sumOf { it.skillTiers.size },
            System.currentTimeMillis() - professionFetchStartTime,
        )

        var skillTiersFetched = 0
        var recipeReferencesDiscovered = 0
        var recipesFetched = 0
        var recipeFailures = 0
        var persistedProfessions = 0
        var persistedSkillTiers = 0
        var persistedCategories = 0
        var persistedRecipes = 0
        var persistedReagents = 0
        var persistedRecipeSlots = 0

        professions.forEachIndexed { professionIndex, profession ->
            log.info(
                "Processing profession {}/{} region={} professionId={} name={} skillTiers={}",
                professionIndex + 1,
                professions.size,
                region,
                profession.id,
                profession.name.en_GB.ifBlank { profession.name.en_US },
                profession.skillTiers.size,
            )
            profession.skillTiers.forEachIndexed { skillTierIndex, skillTier ->
                val tierStartTime = System.currentTimeMillis()
                val recipeIds =
                    skillTier.categories
                        .flatMap { it.recipes }
                        .map(Recipe::id)
                        .distinct()
                recipeReferencesDiscovered += recipeIds.size
                skillTiersFetched += 1
                log.info(
                    "Processing skill tier {}/{} for professionId={} skillTierId={} tierName={} recipesToLoad={} categories={}",
                    skillTierIndex + 1,
                    profession.skillTiers.size,
                    profession.id,
                    skillTier.id,
                    skillTier.name.en_GB.ifBlank { skillTier.name.en_US },
                    recipeIds.size,
                    skillTier.categories.size,
                )

                val recipeFetchStartTime = System.currentTimeMillis()
                val recipeOutcomes =
                    Flux
                        .fromIterable(recipeIds)
                        .flatMap({ recipeId ->
                            Mono
                                .fromCallable { recipeApiClient.getById(recipeId, region) }
                                .map<RecipeFetchOutcome> { recipe ->
                                    RecipeFetchOutcome(recipeId = recipeId, recipe = recipe)
                                }.subscribeOn(Schedulers.boundedElastic())
                                .onErrorResume { error ->
                                    Mono.just(RecipeFetchOutcome(recipeId = recipeId, error = error))
                                }
                        }, PROFESSION_RECIPE_FETCH_CONCURRENCY)
                        .collectList()
                        .block()
                        .orEmpty()
                val fetchedTierRecipes = recipeOutcomes.mapNotNull(RecipeFetchOutcome::recipe)
                val tierRecipeFailures = recipeOutcomes.filter { it.error != null }
                tierRecipeFailures.forEach { failure ->
                    log.warn(
                        "Skipping recipe {} for region {} professionId={} skillTierId={} after fetch failure: {}",
                        failure.recipeId,
                        region,
                        profession.id,
                        skillTier.id,
                        failure.error?.message ?: failure.error?.javaClass?.simpleName ?: "unknown error",
                    )
                }
                recipesFetched += fetchedTierRecipes.size
                recipeFailures += tierRecipeFailures.size
                log.info(
                    "Fetched recipes for professionId={} skillTierId={} success={} failed={} in {}ms",
                    profession.id,
                    skillTier.id,
                    fetchedTierRecipes.size,
                    tierRecipeFailures.size,
                    System.currentTimeMillis() - recipeFetchStartTime,
                )

                if (tierRecipeFailures.isNotEmpty()) {
                    log.warn(
                        "Skipping persistence for professionId={} skillTierId={} because {} recipe fetches failed.",
                        profession.id,
                        skillTier.id,
                        tierRecipeFailures.size,
                    )
                    return@forEachIndexed
                }

                val persistenceStartTime = System.currentTimeMillis()
                val tierSummary =
                    professionRecipeBulkSyncService.syncProfessionSkillTier(
                        profession = profession.copy(skillTiers = emptyList()),
                        skillTier = skillTier,
                        recipes = fetchedTierRecipes,
                        modifiedCraftingSlots = modifiedCraftingSlots,
                    )
                persistedProfessions += tierSummary.professionsUpserted
                persistedSkillTiers += tierSummary.skillTiersUpserted
                persistedCategories += tierSummary.categoriesReplaced
                persistedRecipes += tierSummary.recipesUpserted
                persistedReagents += tierSummary.reagentsReplaced
                persistedRecipeSlots += tierSummary.recipeSlotsReplaced
                log.info(
                    "Persisted professionId={} skillTierId={} recipes={} reagents={} recipeSlots={} in {}ms totalTierMs={}",
                    profession.id,
                    skillTier.id,
                    tierSummary.recipesUpserted,
                    tierSummary.reagentsReplaced,
                    tierSummary.recipeSlotsReplaced,
                    System.currentTimeMillis() - persistenceStartTime,
                    System.currentTimeMillis() - tierStartTime,
                )
            }
        }

        val persistenceSummary =
            ProfessionRecipePersistenceSummary(
                professionsUpserted = persistedProfessions,
                skillTiersUpserted = persistedSkillTiers,
                categoriesReplaced = persistedCategories,
                recipesUpserted = persistedRecipes,
                reagentsReplaced = persistedReagents,
                recipeSlotsReplaced = persistedRecipeSlots,
                modifiedCraftingCategoriesUpserted = modifiedCraftingCategories.size,
                modifiedCraftingSlotsUpserted = modifiedCraftingSlots.size,
                slotCategoryLinksReplaced = modifiedCraftingSlots.sumOf { it.compatibleCategories.size },
            )

        return ProfessionRecipeSyncResult(
            region = region,
            professionsFetched = professions.size,
            skillTiersFetched = skillTiersFetched,
            recipeReferencesDiscovered = recipeReferencesDiscovered,
            recipesFetched = recipesFetched,
            recipeFailures = recipeFailures,
            modifiedCraftingCategoriesFetched = modifiedCraftingCategories.size,
            modifiedCraftingSlotsFetched = modifiedCraftingSlots.size,
            persistenceSummary = persistenceSummary,
            durationMs = System.currentTimeMillis() - startTime,
        ).also { result ->
            log.info(
                "Finished profession/recipe sync for region {} in {}ms professions={} tiers={} recipeRefs={} recipes={} recipeFailures={} modifiedCategories={} modifiedSlots={} persistedRecipes={} persistedSlots={}",
                result.region,
                result.durationMs,
                result.professionsFetched,
                result.skillTiersFetched,
                result.recipeReferencesDiscovered,
                result.recipesFetched,
                result.recipeFailures,
                result.modifiedCraftingCategoriesFetched,
                result.modifiedCraftingSlotsFetched,
                result.persistenceSummary.recipesUpserted,
                result.persistenceSummary.modifiedCraftingSlotsUpserted,
            )
        }
    }
}
