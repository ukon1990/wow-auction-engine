package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionData
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfession
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionDiagnostic
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionInspection
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperRecipe
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.slf4j.LoggerFactory
import java.math.BigDecimal

private const val MAX_TOTAL_PROFESSIONS = 500
private const val MAX_TOTAL_RECIPES = 100_000
private const val MAX_TOTAL_REAGENTS = 500_000
private const val MAX_TOTAL_MAX_QUALITY_REAGENTS = 500_000
private const val MAX_TOTAL_TALENT_NODES = 50_000
private const val MAX_TOTAL_TALENT_ALLOCATIONS = 100_000
private const val MAX_DIAGNOSTIC_EXAMPLES = 10

@Service
class NormalizedAuctionHelperProfessionInspectionService(
    private val validator: Validator,
) {
    private val log = LoggerFactory.getLogger(NormalizedAuctionHelperProfessionInspectionService::class.java)

    fun inspect(payload: NormalizedAuctionHelperProfessionData): NormalizedAuctionHelperProfessionInspection {
        try {
            return inspectValidated(payload)
        } catch (error: ResponseStatusException) {
            log.warn(
                "Normalized profession data rejected (characters={} professions={} recipes={})",
                payload.characters.size,
                payload.characters.sumOf { it.professions.size },
                payload.characters.sumOf { character -> character.professions.sumOf { it.recipes.size } },
            )
            throw error
        }
    }

    private fun inspectValidated(payload: NormalizedAuctionHelperProfessionData): NormalizedAuctionHelperProfessionInspection {
        val violations = validator.validate(payload)
        if (violations.isNotEmpty()) {
            log.warn(
                "Normalized profession data has invalid fields (fields={})",
                violations.map { it.propertyPath.toString() }.distinct().sorted(),
            )
            badRequest(violations.sortedBy { it.propertyPath.toString() }.joinToString("; ") { "${it.propertyPath}: ${it.message}" })
        }
        validateSource(payload)
        val characterKeys = payload.characters.map { it.characterKey }
        if (characterKeys.distinct().size != characterKeys.size) badRequest("Character keys must be unique")
        payload.characters.forEach { character ->
            val professionIds = character.professions.map { it.professionId }
            if (professionIds.distinct().size != professionIds.size) {
                badRequest("Profession IDs must be unique within character ${character.characterKey}")
            }
        }

        val professions = payload.characters.flatMap { it.professions }
        validateCollectionLimits(professions)

        val diagnostics = linkedMapOf<NormalizedAuctionHelperProfessionDiagnostic.Code, DiagnosticAccumulator>()
        var recipesFound = 0
        var recipesWithOutputItemFound = 0
        var missingOutputItemAssociations = 0
        var missingReagentItemAssociations = 0
        var missingCraftingSkillData = 0

        professions.forEach { profession ->
            validateProfession(profession)
            profession.recipes.forEach { recipe ->
                recipesFound++
                validateRecipe(recipe)
                if (recipe.hasOutputItemAssociation()) {
                    recipesWithOutputItemFound++
                } else if (recipe.expectsCraftedItem()) {
                    missingOutputItemAssociations++
                    diagnostics.record(NormalizedAuctionHelperProfessionDiagnostic.Code.CRAFTED_ITEM_MISSING, recipe.recipeId)
                }
                recipe.reagentSlots.filter { it.reagents.isEmpty() }.forEach { slot ->
                    missingReagentItemAssociations++
                    diagnostics.record(NormalizedAuctionHelperProfessionDiagnostic.Code.REAGENT_ITEM_MISSING, recipe.recipeId)
                }
                if (recipe.expectsCraftingSkillData() && !recipe.hasCraftingSkillData()) {
                    missingCraftingSkillData++
                    diagnostics.record(NormalizedAuctionHelperProfessionDiagnostic.Code.CRAFTING_SKILL_DATA_MISSING, recipe.recipeId)
                }
            }
            profession.talents?.let { talents ->
                val nodes = talents.trees.flatMap { it.nodes }.associateBy { it.nodeId }
                talents.allocations.forEach { allocation ->
                    val node = nodes[allocation.nodeId]
                    if (node == null) {
                        diagnostics.record(NormalizedAuctionHelperProfessionDiagnostic.Code.TALENT_ALLOCATION_MISSING_NODE)
                    } else if (node.propertyEntries.none { it.entryId == allocation.entryId }) {
                        diagnostics.record(NormalizedAuctionHelperProfessionDiagnostic.Code.TALENT_ALLOCATION_MISSING_ENTRY)
                    } else {
                        val entry = node.propertyEntries.first { it.entryId == allocation.entryId }
                        val rankLimit = listOfNotNull(node.maxRanks, entry.rankLimit).minOrNull()
                        if (rankLimit != null && allocation.rank > rankLimit) {
                            badRequest("Talent allocation rank exceeds the limit for entry ${allocation.entryId}")
                        }
                    }
                }
            }
        }

        return NormalizedAuctionHelperProfessionInspection(
            imported = false,
            charactersFound = payload.characters.size,
            professionsFound = professions.size,
            recipesFound = recipesFound,
            recipesWithOutputItemFound = recipesWithOutputItemFound,
            missingOutputItemAssociations = missingOutputItemAssociations,
            missingReagentItemAssociations = missingReagentItemAssociations,
            missingCraftingSkillData = missingCraftingSkillData,
            diagnostics = diagnostics.map { (code, accumulator) -> accumulator.toDiagnostic(code) },
        )
    }
}

private fun validateSource(payload: NormalizedAuctionHelperProfessionData) {
    if (payload.source.files.map { it.fileName }.distinct().size != payload.source.files.size) {
        badRequest("Source file names must be unique")
    }
}

private fun validateCollectionLimits(professions: List<NormalizedAuctionHelperProfession>) {
    if (professions.size > MAX_TOTAL_PROFESSIONS) badRequest("Normalized payload exceeds the profession limit")
    if (professions.sumOf { it.recipes.size } > MAX_TOTAL_RECIPES) badRequest("Normalized payload exceeds the recipe limit")
    if (professions.sumOf { profession -> profession.recipes.sumOf { recipe -> recipe.reagentSlots.sumOf { it.reagents.size } } } > MAX_TOTAL_REAGENTS) {
        badRequest("Normalized payload exceeds the reagent limit")
    }
    if (professions.sumOf { profession -> profession.recipes.sumOf { it.maxQualityRequiredReagents.size } } > MAX_TOTAL_MAX_QUALITY_REAGENTS) {
        badRequest("Normalized payload exceeds the max-quality reagent association limit")
    }
    if (professions.sumOf { profession -> profession.talents?.trees?.sumOf { it.nodes.size } ?: 0 } > MAX_TOTAL_TALENT_NODES) {
        badRequest("Normalized payload exceeds the talent node limit")
    }
    if (professions.sumOf { it.talents?.allocations?.size ?: 0 } > MAX_TOTAL_TALENT_ALLOCATIONS) {
        badRequest("Normalized payload exceeds the talent allocation limit")
    }
}

private fun validateProfession(profession: NormalizedAuctionHelperProfession) {
    val recipeIds = profession.recipes.map { it.recipeId }
    if (recipeIds.distinct().size != recipeIds.size) {
        badRequest("Recipe IDs must be unique within profession ${profession.professionId}")
    }
    if (profession.skillLevel != null && profession.maxSkillLevel != null && profession.skillLevel > profession.maxSkillLevel) {
        badRequest("Profession ${profession.professionId} skill level exceeds its maximum")
    }
    profession.talents?.trees?.forEach { tree ->
        val nodeIds = tree.nodes.map { it.nodeId }
        if (nodeIds.distinct().size != nodeIds.size) badRequest("Talent node IDs must be unique within tree ${tree.treeId}")
        tree.nodes.forEach { node ->
            val entryIds = node.propertyEntries.map { it.entryId }
            if (entryIds.distinct().size != entryIds.size) badRequest("Talent entry IDs must be unique within node ${node.nodeId}")
        }
    }
}

private fun validateRecipe(recipe: NormalizedAuctionHelperRecipe) {
    val qualities = recipe.qualityOutputItemIds.map { it.quality }
    if (qualities.distinct().size != qualities.size) badRequest("Recipe ${recipe.recipeId} has duplicate output quality associations")

    val slotIndexes = recipe.reagentSlots.map { it.slotIndex }
    if (slotIndexes.distinct().size != slotIndexes.size) badRequest("Recipe ${recipe.recipeId} has duplicate reagent slot indexes")
    recipe.reagentSlots.forEach { slot ->
        if (slot.quantity <= BigDecimal.ZERO) badRequest("Recipe ${recipe.recipeId} reagent slot quantity must be positive")
        val reagentKeys = slot.reagents.map { it.itemId to it.quality }
        if (reagentKeys.distinct().size != reagentKeys.size) {
            badRequest("Recipe ${recipe.recipeId} reagent slot ${slot.slotIndex} has duplicate item associations")
        }
        if (slot.reagents.any { it.quantity <= BigDecimal.ZERO }) {
            badRequest("Recipe ${recipe.recipeId} reagent quantities must be positive")
        }
    }
    recipe.maxQualityRequiredReagents.forEach { association ->
        if (association.slotIndex == null && association.dataSlotIndex == null) {
            badRequest("Recipe ${recipe.recipeId} max-quality reagent association requires a slot or data-slot index")
        }
        if (association.quantity <= BigDecimal.ZERO) {
            badRequest("Recipe ${recipe.recipeId} max-quality reagent quantities must be positive")
        }
        val slotMatches = association.slotIndex?.let { index -> recipe.reagentSlots.any { it.slotIndex == index } } ?: false
        val dataSlotMatches = association.dataSlotIndex?.let { index -> recipe.reagentSlots.any { it.dataSlotIndex == index } } ?: false
        if (!slotMatches && !dataSlotMatches) {
            badRequest("Recipe ${recipe.recipeId} max-quality reagent association references an unknown slot")
        }
        val matchingSlots = recipe.reagentSlots.filter { it.slotIndex == association.slotIndex || it.dataSlotIndex == association.dataSlotIndex }
        if (matchingSlots.none { slot -> slot.reagents.any { it.itemId == association.itemId } }) {
            badRequest("Recipe ${recipe.recipeId} max-quality reagent association references an item absent from its slot")
        }
    }

    if (recipe.qualityThresholds.any { it < BigDecimal.ZERO }) badRequest("Recipe ${recipe.recipeId} quality thresholds cannot be negative")
    if (!recipe.qualityThresholds.isMonotonicallyIncreasing()) {
        badRequest("Recipe ${recipe.recipeId} quality thresholds must be monotonically increasing")
    }
    if (recipe.lowerSkillThreshold != null && recipe.upperSkillThreshold != null && recipe.lowerSkillThreshold > recipe.upperSkillThreshold) {
        badRequest("Recipe ${recipe.recipeId} lower skill threshold exceeds its upper threshold")
    }
}

private fun NormalizedAuctionHelperRecipe.hasOutputItemAssociation(): Boolean =
    craftedItemId != null || outputItemId != null || qualityOutputItemIds.isNotEmpty()

private fun NormalizedAuctionHelperRecipe.expectsCraftedItem(): Boolean {
    if (isGatheringRecipe == true || isEnchantingRecipe == true || isSalvageRecipe == true || isRecraft == true) return false
    return supportsQualities == true || recipeType == ITEM_RECIPE_TYPE
}

private fun NormalizedAuctionHelperRecipe.expectsCraftingSkillData(): Boolean =
    supportsQualities == true || hasCraftingOperationInfo == true

private fun NormalizedAuctionHelperRecipe.hasCraftingSkillData(): Boolean =
    baseDifficulty != null ||
        baseSkill != null ||
        bonusSkill != null ||
        requiredReagentSkillDelta != null ||
        lowerSkillThreshold != null ||
        upperSkillThreshold != null ||
        qualityThresholds.isNotEmpty()

private fun List<BigDecimal>.isMonotonicallyIncreasing(): Boolean = zipWithNext().all { (left, right) -> left <= right }

private data class DiagnosticAccumulator(
    var count: Int = 0,
    val exampleRecipeIds: MutableList<Int> = mutableListOf(),
)

private fun MutableMap<NormalizedAuctionHelperProfessionDiagnostic.Code, DiagnosticAccumulator>.record(
    code: NormalizedAuctionHelperProfessionDiagnostic.Code,
    recipeId: Int? = null,
) {
    val accumulator = getOrPut(code) { DiagnosticAccumulator() }
    accumulator.count++
    if (recipeId != null && recipeId !in accumulator.exampleRecipeIds && accumulator.exampleRecipeIds.size < MAX_DIAGNOSTIC_EXAMPLES) {
        accumulator.exampleRecipeIds.add(recipeId)
    }
}

private fun DiagnosticAccumulator.toDiagnostic(code: NormalizedAuctionHelperProfessionDiagnostic.Code) =
    NormalizedAuctionHelperProfessionDiagnostic(
        code = code,
        detail = DIAGNOSTIC_DETAILS.getValue(code),
        count = count,
        exampleRecipeIds = exampleRecipeIds,
    )

private const val ITEM_RECIPE_TYPE = 1

private val DIAGNOSTIC_DETAILS =
    mapOf(
        NormalizedAuctionHelperProfessionDiagnostic.Code.CRAFTED_ITEM_MISSING to "Recipes expected to create items have no output item association.",
        NormalizedAuctionHelperProfessionDiagnostic.Code.REAGENT_ITEM_MISSING to "Recipe reagent slots have no item association.",
        NormalizedAuctionHelperProfessionDiagnostic.Code.CRAFTING_SKILL_DATA_MISSING to "Recipes using quality or crafting operations have no skill or difficulty data.",
        NormalizedAuctionHelperProfessionDiagnostic.Code.TALENT_ALLOCATION_MISSING_NODE to "Talent allocations reference nodes absent from their profession trees.",
        NormalizedAuctionHelperProfessionDiagnostic.Code.TALENT_ALLOCATION_MISSING_ENTRY to "Talent allocations reference entries absent from their nodes.",
    )

private fun badRequest(detail: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, detail)
