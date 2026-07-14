package net.jonasmf.auctionengine.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProfessionRecipeSyncProgressTest {
    @Test
    fun `progress percent reserves early phases and scales by completed tiers`() {
        assertEquals(
            0,
            ProfessionRecipeSyncProgress(phase = PHASE_FETCHING_METADATA).progressPercent(),
        )
        assertEquals(
            5,
            ProfessionRecipeSyncProgress(phase = PHASE_FETCHING_PROFESSIONS).progressPercent(),
        )
        assertEquals(
            10,
            ProfessionRecipeSyncProgress(
                phase = PHASE_PROCESSING_SKILL_TIER,
                skillTiersCompleted = 0,
                skillTiersTotal = 10,
            ).progressPercent(),
        )
        assertEquals(
            55,
            ProfessionRecipeSyncProgress(
                phase = PHASE_PROCESSING_SKILL_TIER,
                skillTiersCompleted = 5,
                skillTiersTotal = 10,
            ).progressPercent(),
        )
    }
}
