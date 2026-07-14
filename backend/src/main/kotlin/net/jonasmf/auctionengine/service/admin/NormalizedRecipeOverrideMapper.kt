package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.AdminRecipeOutput
import net.jonasmf.auctionengine.generated.model.AdminRecipeOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipeReagent
import net.jonasmf.auctionengine.generated.model.AdminRecipeReagentRank
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperRecipe
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperReagentSlot
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

internal object NormalizedRecipeOverrideMapper {
    fun toOverrideRequest(
        recipe: NormalizedAuctionHelperRecipe,
        importId: Long,
    ): AdminRecipeOverrideRequest? {
        if (!hasSyncableData(recipe)) return null

        val outputs = mapOutputs(recipe)
        val reagents = mapReagents(recipe)
        val primaryOutputItemId = primaryOutputItemId(recipe)

        return AdminRecipeOverrideRequest(
            craftedItemId = primaryOutputItemId,
            craftedQuantity = outputs?.singleOrNull()?.craftedQuantity ?: 1,
            rank = recipeRank(recipe, outputs),
            requiredSkillLevel = recipeRequiredSkillLevel(recipe),
            outputs = outputs,
            reagents = reagents,
            overrideNote = "Auction Helper import #$importId",
        )
    }

    fun hasSyncableData(recipe: NormalizedAuctionHelperRecipe): Boolean =
        primaryOutputItemId(recipe) != null ||
            mapReagents(recipe)?.isNotEmpty() == true ||
            hasCraftingSkillOverrideData(recipe)

    private fun hasCraftingSkillOverrideData(recipe: NormalizedAuctionHelperRecipe): Boolean =
        recipe.baseSkill != null ||
            recipe.lowerSkillThreshold != null ||
            recipe.upperSkillThreshold != null ||
            recipe.qualityThresholds.isNotEmpty()

    private fun primaryOutputItemId(recipe: NormalizedAuctionHelperRecipe): Int? =
        recipe.qualityOutputItemIds
            .minByOrNull { it.quality }
            ?.itemId
            ?: recipe.outputItemId
            ?: recipe.craftedItemId

    private fun mapOutputs(recipe: NormalizedAuctionHelperRecipe): List<AdminRecipeOutput>? {
        if (recipe.qualityOutputItemIds.isNotEmpty()) {
            return recipe.qualityOutputItemIds
                .sortedBy { it.quality }
                .mapIndexed { index, output ->
                    AdminRecipeOutput(
                        sortOrder = index,
                        craftedItemId = output.itemId,
                        craftedQuantity = 1,
                        requiredSkillLevel = outputRequiredSkillLevel(recipe, output.quality),
                    )
                }
        }
        val itemId = primaryOutputItemId(recipe) ?: return null
        return listOf(
            AdminRecipeOutput(
                sortOrder = 0,
                craftedItemId = itemId,
                craftedQuantity = 1,
                requiredSkillLevel = recipeRequiredSkillLevel(recipe),
            ),
        )
    }

    private fun mapReagents(recipe: NormalizedAuctionHelperRecipe): List<AdminRecipeReagent>? {
        val slots =
            recipe.reagentSlots
                .filter { slot -> slot.reagents.isNotEmpty() }
                .sortedBy { it.slotIndex }
        if (slots.isEmpty()) return null

        return slots.mapIndexed { index, slot -> mapReagentSlot(slot, index) }
    }

    private fun mapReagentSlot(
        slot: NormalizedAuctionHelperReagentSlot,
        sortOrder: Int,
    ): AdminRecipeReagent {
        val rankedReagents =
            slot.reagents
                .filter { it.quality != null && it.quality in 1..3 }
                .sortedBy { it.quality }
        val baseReagent =
            rankedReagents.firstOrNull()
                ?: slot.reagents.minByOrNull { it.itemId }
                ?: slot.reagents.first()

        val quantity = resolveQuantity(slot, baseReagent.quantity)
        val ranks =
            rankedReagents
                .map {
                    AdminRecipeReagentRank(
                        rank = it.quality!!,
                        itemId = it.itemId,
                    )
                }.distinctBy { it.rank }

        return AdminRecipeReagent(
            itemId = baseReagent.itemId,
            quantity = quantity,
            sortOrder = sortOrder,
            ranks = ranks,
        )
    }

    private fun resolveQuantity(
        slot: NormalizedAuctionHelperReagentSlot,
        fallbackQuantity: BigDecimal,
    ): Int {
        val slotQuantity = slot.quantity.toPositiveIntOrNull()
        if (slotQuantity != null) return slotQuantity

        val reagentQuantity =
            slot.reagents
                .mapNotNull { it.quantity.toPositiveIntOrNull() }
                .maxOrNull()
        return max(reagentQuantity ?: fallbackQuantity.toPositiveIntOrNull() ?: 1, 1)
    }

    private fun recipeRank(
        recipe: NormalizedAuctionHelperRecipe,
        outputs: List<AdminRecipeOutput>?,
    ): Int? {
        if ((outputs?.size ?: 0) > 1) return null
        if (recipe.supportsQualities != true) return null
        return recipe.qualityOutputItemIds.minByOrNull { it.quality }?.quality?.coerceIn(1, 3)
    }

    private fun recipeRequiredSkillLevel(recipe: NormalizedAuctionHelperRecipe): Int? =
        recipe.lowerSkillThreshold?.toRequiredSkillLevelInt()
            ?: recipe.baseSkill?.toRequiredSkillLevelInt()

    private fun outputRequiredSkillLevel(
        recipe: NormalizedAuctionHelperRecipe,
        quality: Int,
    ): Int? {
        if (recipe.qualityThresholds.isNotEmpty() && quality > 0) {
            recipe.qualityThresholds.getOrNull(quality - 1)?.toRequiredSkillLevelInt()?.let { return it }
        }
        return recipe.upperSkillThreshold?.toRequiredSkillLevelInt()
            ?: recipe.baseSkill?.toRequiredSkillLevelInt()
    }

    private fun BigDecimal.toRequiredSkillLevelInt(): Int? {
        if (this < BigDecimal.ZERO) return null
        return setScale(0, RoundingMode.CEILING).toInt()
    }

    private fun BigDecimal.toPositiveIntOrNull(): Int? {
        if (this <= BigDecimal.ZERO) return null
        return setScale(0, RoundingMode.CEILING).toInt()
    }
}
