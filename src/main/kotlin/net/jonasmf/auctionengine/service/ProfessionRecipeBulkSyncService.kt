package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.dbo.rds.profession.ModifiedCraftingCategoryMetadataDBO
import net.jonasmf.auctionengine.dbo.rds.profession.ModifiedCraftingSlotMetadataDBO
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingCategory
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingSlot
import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.ProfessionCategory
import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.mapper.toDBO
import net.jonasmf.auctionengine.repository.rds.ModifiedCraftingCategoryMetadataRepository
import net.jonasmf.auctionengine.repository.rds.ModifiedCraftingSlotMetadataRepository
import net.jonasmf.auctionengine.repository.rds.ProfessionRecipeJdbcRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ProfessionRecipePersistenceSummary(
    val professionsUpserted: Int,
    val skillTiersUpserted: Int,
    val categoriesReplaced: Int,
    val recipesUpserted: Int,
    val reagentsReplaced: Int,
    val recipeSlotsReplaced: Int,
    val modifiedCraftingCategoriesUpserted: Int,
    val modifiedCraftingSlotsUpserted: Int,
    val slotCategoryLinksReplaced: Int,
)

@Service
class ProfessionRecipeBulkSyncService(
    private val modifiedCraftingCategoryMetadataRepository: ModifiedCraftingCategoryMetadataRepository,
    private val modifiedCraftingSlotMetadataRepository: ModifiedCraftingSlotMetadataRepository,
    private val professionRecipeJdbcRepository: ProfessionRecipeJdbcRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(ProfessionRecipeBulkSyncService::class.java)

    @Transactional
    fun sync(
        professions: List<Profession>,
        recipes: List<Recipe>,
        modifiedCraftingCategories: List<ModifiedCraftingCategory>,
        modifiedCraftingSlots: List<ModifiedCraftingSlot>,
    ): ProfessionRecipePersistenceSummary {
        syncModifiedCraftingMetadata(modifiedCraftingCategories, modifiedCraftingSlots)

        var professionsUpserted = 0
        var skillTiersUpserted = 0
        var categoriesReplaced = 0
        var recipesUpserted = 0
        var reagentsReplaced = 0
        var recipeSlotsReplaced = 0

        professions.forEach { profession ->
            profession.skillTiers.forEach { skillTier ->
                val summary = syncProfessionSkillTier(profession.copy(skillTiers = emptyList()), skillTier, recipes, modifiedCraftingSlots)
                professionsUpserted += summary.professionsUpserted
                skillTiersUpserted += summary.skillTiersUpserted
                categoriesReplaced += summary.categoriesReplaced
                recipesUpserted += summary.recipesUpserted
                reagentsReplaced += summary.reagentsReplaced
                recipeSlotsReplaced += summary.recipeSlotsReplaced
            }
        }

        return ProfessionRecipePersistenceSummary(
            professionsUpserted = professionsUpserted,
            skillTiersUpserted = skillTiersUpserted,
            categoriesReplaced = categoriesReplaced,
            recipesUpserted = recipesUpserted,
            reagentsReplaced = reagentsReplaced,
            recipeSlotsReplaced = recipeSlotsReplaced,
            modifiedCraftingCategoriesUpserted = modifiedCraftingCategories.size,
            modifiedCraftingSlotsUpserted = modifiedCraftingSlots.size,
            slotCategoryLinksReplaced = modifiedCraftingSlots.sumOf { it.compatibleCategories.size },
        )
    }

    @Transactional
    fun syncModifiedCraftingMetadata(
        modifiedCraftingCategories: List<ModifiedCraftingCategory>,
        modifiedCraftingSlots: List<ModifiedCraftingSlot>,
    ) {
        upsertModifiedCraftingMetadata(modifiedCraftingCategories, modifiedCraftingSlots)
    }

    @Transactional
    fun syncProfessionSkillTier(
        profession: Profession,
        skillTier: SkillTier,
        recipes: List<Recipe>,
        modifiedCraftingSlots: List<ModifiedCraftingSlot>,
    ): ProfessionRecipePersistenceSummary {
        val recipesById = recipes.associateBy { it.id }
        val slotsById = modifiedCraftingSlots.associateBy { it.id }
        val enrichedSkillTier = skillTier.resolveRecipeDetails(recipesById, slotsById)
        professionRecipeJdbcRepository.syncProfessionSkillTier(profession, enrichedSkillTier)

        val enrichedRecipes = enrichedSkillTier.categories.flatMap(ProfessionCategory::recipes)
        val summary =
            ProfessionRecipePersistenceSummary(
                professionsUpserted = 1,
                skillTiersUpserted = 1,
                categoriesReplaced = enrichedSkillTier.categories.size,
                recipesUpserted = enrichedRecipes.size,
                reagentsReplaced = enrichedRecipes.sumOf { it.reagents.size },
                recipeSlotsReplaced = enrichedRecipes.sumOf { it.modifiedCraftingSlots.size },
                modifiedCraftingCategoriesUpserted = 0,
                modifiedCraftingSlotsUpserted = 0,
                slotCategoryLinksReplaced = 0,
            )

        log.info(
            "Persisted profession skill tier summary professionId={} skillTierId={} categories={} recipes={} reagents={} recipeSlots={}",
            profession.id,
            enrichedSkillTier.id,
            summary.categoriesReplaced,
            summary.recipesUpserted,
            summary.reagentsReplaced,
            summary.recipeSlotsReplaced,
        )

        return summary
    }

    private fun upsertModifiedCraftingMetadata(
        modifiedCraftingCategories: List<ModifiedCraftingCategory>,
        modifiedCraftingSlots: List<ModifiedCraftingSlot>,
    ) {
        modifiedCraftingSlotMetadataRepository.deleteAllInBatch()
        modifiedCraftingCategoryMetadataRepository.deleteAllInBatch()
        jdbcTemplate.update(
            "DELETE FROM locale_dbo WHERE source_type IN (?, ?)",
            LocaleSourceType.MODIFIED_CRAFTING_CATEGORY_METADATA,
            LocaleSourceType.MODIFIED_CRAFTING_SLOT_METADATA,
        )

        if (modifiedCraftingCategories.isNotEmpty()) {
            modifiedCraftingCategoryMetadataRepository.saveAll(
                modifiedCraftingCategories.map {
                    ModifiedCraftingCategoryMetadataDBO(
                        id = it.id,
                        name =
                            it.name.toDBO(
                                LocaleSourceType.MODIFIED_CRAFTING_CATEGORY_METADATA,
                                localeSourceKey(it.id),
                                "name",
                            ),
                    )
                },
            )
        }

        if (modifiedCraftingSlots.isNotEmpty()) {
            modifiedCraftingSlotMetadataRepository.saveAll(
                modifiedCraftingSlots.map {
                    ModifiedCraftingSlotMetadataDBO(
                        id = it.id,
                        description =
                            it.description.toDBO(
                                LocaleSourceType.MODIFIED_CRAFTING_SLOT_METADATA,
                                localeSourceKey(it.id),
                                "description",
                            ),
                        compatibleCategoryIds = it.compatibleCategories.map(ModifiedCraftingCategory::id).toMutableSet(),
                    )
                },
            )
        }
    }

    private fun SkillTier.resolveRecipeDetails(
        recipesById: Map<Int, Recipe>,
        slotsById: Map<Int, ModifiedCraftingSlot>,
    ): SkillTier =
        copy(
            categories =
                categories.map { category ->
                    category.copy(
                        recipes =
                            category.recipes.mapNotNull { recipe ->
                                recipesById[recipe.id]?.enrichSlots(slotsById)
                            },
                    )
                },
        )

    private fun Recipe.enrichSlots(slotsById: Map<Int, ModifiedCraftingSlot>): Recipe =
        copy(
            modifiedCraftingSlots =
                modifiedCraftingSlots.map { slot ->
                    val metadata = slotsById[slot.id] ?: return@map slot
                    metadata.copy(displayOrder = slot.displayOrder)
                },
        )
}
