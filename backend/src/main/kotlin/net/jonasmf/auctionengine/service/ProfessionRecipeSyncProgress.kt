package net.jonasmf.auctionengine.service

data class ProfessionRecipeSyncProgress(
    val phase: String,
    val professionIndex: Int? = null,
    val professionTotal: Int? = null,
    val professionName: String? = null,
    val skillTierIndex: Int? = null,
    val skillTierTotal: Int? = null,
    val skillTierName: String? = null,
    val skillTiersCompleted: Int = 0,
    val skillTiersTotal: Int? = null,
    val recipesInTier: Int? = null,
    val recipesFetched: Int = 0,
    val recipeFailures: Int = 0,
) {
    fun toSummaryMap(): Map<String, Any> =
        buildMap {
            put("phase", phase)
            professionIndex?.let { put("professionIndex", it) }
            professionTotal?.let { put("professionTotal", it) }
            professionName?.let { put("professionName", it) }
            skillTierIndex?.let { put("skillTierIndex", it) }
            skillTierTotal?.let { put("skillTierTotal", it) }
            skillTierName?.let { put("skillTierName", it) }
            put("skillTiersCompleted", skillTiersCompleted)
            skillTiersTotal?.let { put("skillTiersTotal", it) }
            recipesInTier?.let { put("recipesInTier", it) }
            put("recipesFetched", recipesFetched)
            put("recipeFailures", recipeFailures)
            progressPercent()?.let { put("progressPercent", it) }
        }

    fun progressPercent(): Int? {
        val totalTiers = skillTiersTotal ?: return when (phase) {
            PHASE_FETCHING_METADATA -> 0
            PHASE_FETCHING_PROFESSIONS -> 5
            else -> null
        }
        if (totalTiers <= 0) return 100
        val tierProgress = skillTiersCompleted.coerceAtMost(totalTiers).toDouble() / totalTiers
        return (10 + tierProgress * 90).toInt().coerceIn(0, 99)
    }
}

const val PHASE_FETCHING_METADATA = "fetching_metadata"
const val PHASE_FETCHING_PROFESSIONS = "fetching_professions"
const val PHASE_PROCESSING_SKILL_TIER = "processing_skill_tier"
