package net.jonasmf.auctionengine

import io.mockk.every
import io.mockk.mockk
import net.jonasmf.auctionengine.service.RuntimeHealthSnapshot
import net.jonasmf.auctionengine.service.RuntimeHealthTracker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class HealthControllerTest {
    private val runtimeHealthTracker = mockk<RuntimeHealthTracker>()
    private val controller = HealthController(runtimeHealthTracker)

    @Test
    fun `health endpoint returns no content`() {
        every { runtimeHealthTracker.snapshot(any()) } returns RuntimeHealthSnapshot(healthy = true)

        val response = controller.health()

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `health endpoint returns service unavailable when runtime tracker is unhealthy`() {
        every { runtimeHealthTracker.snapshot(any()) } returns
            RuntimeHealthSnapshot(healthy = false, reason = "stalled")

        val response = controller.health()

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(null, response.headers.getFirst("X-Health-Reason"))
    }
}
