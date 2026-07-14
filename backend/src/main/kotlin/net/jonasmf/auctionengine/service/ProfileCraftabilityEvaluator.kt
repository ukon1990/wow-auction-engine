package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.repository.rds.CraftingProfileCandidate
import net.jonasmf.auctionengine.repository.rds.ProfessionSkillTreeNodeEffect
import net.jonasmf.auctionengine.repository.rds.ProfessionSkillTreeNodeEffectRepository
import net.jonasmf.auctionengine.repository.rds.ProfileRepository
import net.jonasmf.auctionengine.repository.rds.RecipeCraftingRule
import net.jonasmf.auctionengine.repository.rds.RecipeCraftingRuleRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

data class ProfileCraftabilityEvaluation(
    val craftable: Boolean,
    val predictedQuality: Int,
    val effectiveSkill: BigDecimal,
)

@Service
class ProfileCraftabilityEvaluator(
    private val recipeCraftingRuleRepository: RecipeCraftingRuleRepository,
    private val professionSkillTreeNodeEffectRepository: ProfessionSkillTreeNodeEffectRepository,
    private val profileRepository: ProfileRepository,
) {
    fun evaluateCandidates(
        recipeId: Int,
        candidates: List<CraftingProfileCandidate>,
    ): List<Pair<CraftingProfileCandidate, ProfileCraftabilityEvaluation>> {
        if (candidates.isEmpty()) return emptyList()
        val rule = recipeCraftingRuleRepository.findByRecipeId(recipeId) ?: return emptyList()
        val treeIds = candidates.mapNotNull { it.treeId }.toSet()
        if (treeIds.isEmpty()) return emptyList()
        val effectsByTree = professionSkillTreeNodeEffectRepository.findByTreeIds(treeIds)
        if (effectsByTree.values.all { it.isEmpty() }) return emptyList()

        val profileIds = candidates.map { it.profileId }
        val allocationsByProfile = profileRepository.loadProfileAllocations(profileIds)
        val entryNodeIdsByTree = profileRepository.loadEntryNodeIdsByTreeIds(treeIds)

        return candidates.mapNotNull { candidate ->
            val treeId = candidate.treeId ?: return@mapNotNull null
            val effects = effectsByTree[treeId].orEmpty()
            if (effects.isEmpty()) return@mapNotNull null
            val allocations = allocationsByProfile[candidate.profileId].orEmpty()
            if (allocations.isEmpty()) return@mapNotNull null
            val rankByNode =
                profileRepository.summarizeNodeRanks(
                    entryNodeIdsByTree.getValue(treeId),
                    allocations,
                )
            evaluateWithPreparedData(rule, effects, rankByNode)?.let { evaluation ->
                candidate.copy(predictedQuality = evaluation.predictedQuality) to evaluation
            }
        }
    }

    private fun evaluateWithPreparedData(
        rule: RecipeCraftingRule,
        effects: List<ProfessionSkillTreeNodeEffect>,
        rankByNode: Map<Long, Int>,
    ): ProfileCraftabilityEvaluation? {
        val talentBonus = effects.filter { isEffectActive(it, rankByNode) }.sumOf { it.skillBonus }
        val baseSkill = rule.baseSkill ?: BigDecimal.ZERO
        val effectiveSkill = baseSkill + BigDecimal(talentBonus)
        val predictedQuality = predictQuality(effectiveSkill, rule)
        return ProfileCraftabilityEvaluation(
            craftable = predictedQuality > 0,
            predictedQuality = predictedQuality,
            effectiveSkill = effectiveSkill,
        )
    }

    private fun isEffectActive(
        effect: ProfessionSkillTreeNodeEffect,
        rankByNode: Map<Long, Int>,
    ): Boolean {
        val nodeRank = rankByNode[effect.nodeId] ?: 0
        if (nodeRank < effect.unlockRank) return false
        if (effect.requiredParentRanks <= 0) return nodeRank > 0
        return nodeRank > 0
    }

    private fun predictQuality(
        effectiveSkill: BigDecimal,
        rule: RecipeCraftingRule,
    ): Int {
        if (rule.qualityThresholds.isEmpty()) return if (effectiveSkill >= (rule.baseSkill ?: BigDecimal.ZERO)) 1 else 0
        var quality = 0
        rule.qualityThresholds.forEachIndexed { index, threshold ->
            if (effectiveSkill >= threshold) quality = index + 1
        }
        return quality
    }
}
