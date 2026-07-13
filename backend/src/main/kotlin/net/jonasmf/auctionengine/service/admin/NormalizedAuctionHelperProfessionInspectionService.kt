package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionData
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfession
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionDiagnostic
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionInspection
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperRecipe
import net.jonasmf.auctionengine.repository.rds.NormalizedProfessionImportRepository
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val repository: NormalizedProfessionImportRepository,
) {
    private val log = LoggerFactory.getLogger(NormalizedAuctionHelperProfessionInspectionService::class.java)

    @Transactional
    fun inspect(
        payload: NormalizedAuctionHelperProfessionData,
        ownerSubject: String = "admin",
    ): NormalizedAuctionHelperProfessionInspection {
        try {
            return inspectValidated(payload, ownerSubject)
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

    private fun inspectValidated(
        payload: NormalizedAuctionHelperProfessionData,
        ownerSubject: String,
    ): NormalizedAuctionHelperProfessionInspection {
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
        val missingProfessionIds = repository.missingProfessionIds(professions.map { it.professionId }.toSet())
        if (missingProfessionIds.isNotEmpty()) {
            badRequest("Profession IDs are missing from the catalog: ${missingProfessionIds.sorted().joinToString()}")
        }

        val diagnostics = linkedMapOf<NormalizedAuctionHelperProfessionDiagnostic.Code, DiagnosticAccumulator>()
        var recipesFound = 0
        var recipesWithOutputItemFound = 0
        var missingOutputItemAssociations = 0
        var missingReagentItemAssociations = 0
        var missingCraftingSkillData = 0

        professions.forEach { profession ->
            validateProfession(profession)
            if (profession.talents == null || profession.talents.trees.isEmpty()) {
                diagnostics.record(NormalizedAuctionHelperProfessionDiagnostic.Code.TALENT_DATA_MISSING)
            }
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
                recipe.maxQualityRequiredReagents.forEach { association ->
                    if (!recipe.hasMatchingReagentAssociation(association.slotIndex, association.dataSlotIndex, association.itemId)) {
                        diagnostics.record(
                            NormalizedAuctionHelperProfessionDiagnostic.Code.MAX_QUALITY_REAGENT_ASSOCIATION_INCOMPLETE,
                            recipe.recipeId,
                        )
                    }
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

        repository.save(payload, professions.size, recipesFound, ownerSubject)

        return NormalizedAuctionHelperProfessionInspection(
            imported = true,
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
        val reagentKeys = slot.reagents.map { it.itemId to it.quality }
        if (reagentKeys.distinct().size != reagentKeys.size) {
            badRequest("Recipe ${recipe.recipeId} reagent slot ${slot.slotIndex} has duplicate item associations")
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

private fun NormalizedAuctionHelperRecipe.hasMatchingReagentAssociation(
    slotIndex: Int?,
    dataSlotIndex: Int?,
    itemId: Int,
): Boolean =
    reagentSlots
        .filter { slot -> slot.slotIndex == slotIndex || (dataSlotIndex != null && slot.dataSlotIndex == dataSlotIndex) }
        .any { slot -> slot.reagents.any { it.itemId == itemId } }

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
        NormalizedAuctionHelperProfessionDiagnostic.Code.MAX_QUALITY_REAGENT_ASSOCIATION_INCOMPLETE to "Maximum-quality reagent metadata could not be matched to an exported recipe slot.",
        NormalizedAuctionHelperProfessionDiagnostic.Code.TALENT_DATA_MISSING to "No specialization tree definitions were exported for a profession; shared talent-tree tables were not changed.",
        NormalizedAuctionHelperProfessionDiagnostic.Code.TALENT_ALLOCATION_MISSING_NODE to "Talent allocations reference nodes absent from their profession trees.",
        NormalizedAuctionHelperProfessionDiagnostic.Code.TALENT_ALLOCATION_MISSING_ENTRY to "Talent allocations reference entries absent from their nodes.",
    )

private fun badRequest(detail: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, detail)
