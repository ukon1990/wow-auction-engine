package net.jonasmf.auctionengine

import net.jonasmf.auctionengine.service.RuntimeHealthTracker
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val runtimeHealthTracker: RuntimeHealthTracker,
) {
    @GetMapping("/health")
    fun health(): ResponseEntity<Void> {
        val snapshot = runtimeHealthTracker.snapshot()
        return if (snapshot.healthy) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.status(503).header("X-Health-Reason", snapshot.reason ?: "stalled").build()
        }
    }
}
