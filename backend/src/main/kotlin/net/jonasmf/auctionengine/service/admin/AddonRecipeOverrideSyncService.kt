package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperRecipe
import net.jonasmf.auctionengine.repository.rds.AdminRecipeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class AddonRecipeOverrideSyncSummary(
    val attempted: Int = 0,
    val applied: Int = 0,
    val skippedNoBaseRecipe: Int = 0,
    val skippedNoData: Int = 0,
)

@Service
class AddonRecipeOverrideSyncService(
    private val adminRecipeRepository: AdminRecipeRepository,
) {
    private val log = LoggerFactory.getLogger(AddonRecipeOverrideSyncService::class.java)

    fun syncRecipes(
        recipes: Collection<NormalizedAuctionHelperRecipe>,
        importId: Long,
    ): AddonRecipeOverrideSyncSummary {
        var attempted = 0
        var applied = 0
        var skippedNoBaseRecipe = 0
        var skippedNoData = 0

        recipes.forEach { recipe ->
            if (!NormalizedRecipeOverrideMapper.hasSyncableData(recipe)) {
                skippedNoData++
                return@forEach
            }
            if (!adminRecipeRepository.hasBaseRecipe(recipe.recipeId)) {
                skippedNoBaseRecipe++
                log.debug(
                    "Skipping addon recipe override for recipeId={} because no base recipe exists",
                    recipe.recipeId,
                )
                return@forEach
            }

            attempted++
            val request =
                NormalizedRecipeOverrideMapper.toOverrideRequest(recipe, importId)
                    ?: run {
                        skippedNoData++
                        return@forEach
                    }
            adminRecipeRepository.upsertOverride(recipe.recipeId, request)
            applied++
        }

        if (applied > 0) {
            log.info(
                "Applied addon recipe overrides importId={} applied={} attempted={} skippedNoBaseRecipe={} skippedNoData={}",
                importId,
                applied,
                attempted,
                skippedNoBaseRecipe,
                skippedNoData,
            )
        }

        return AddonRecipeOverrideSyncSummary(
            attempted = attempted,
            applied = applied,
            skippedNoBaseRecipe = skippedNoBaseRecipe,
            skippedNoData = skippedNoData,
        )
    }
}
