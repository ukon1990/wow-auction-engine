package net.jonasmf.auctionengine

import net.jonasmf.auctionengine.service.RuntimeHealthTracker
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val runtimeHealthTracker: RuntimeHealthTracker,
) {
    private val logger = LoggerFactory.getLogger(HealthController::class.java)

    @GetMapping("/health")
    fun health(): ResponseEntity<Void> {
        val snapshot = runtimeHealthTracker.snapshot()
        return if (snapshot.healthy) {
            ResponseEntity.noContent().build()
        } else {
            logger.warn("Health check failed: {}", snapshot.reason ?: "runtime unhealthy")
            ResponseEntity.status(503).build()
        }
    }
}
