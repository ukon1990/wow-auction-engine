package net.jonasmf.auctionengine.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class RuntimeHealthTrackerTest {
    @Test
    fun `snapshot stays healthy when no batch is running`() {
        val tracker = RuntimeHealthTracker(Duration.ofMinutes(20))

        assertTrue(tracker.snapshot().healthy)
    }

    @Test
    fun `snapshot becomes unhealthy when batch stalls beyond threshold`() {
        val tracker = RuntimeHealthTracker(Duration.ofMillis(1))

        tracker.markUpdateBatchStarted(emptyList())
        Thread.sleep(10)

        val snapshot = tracker.snapshot()

        assertFalse(snapshot.healthy)
    }
}
