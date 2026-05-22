package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.HealthApi
import net.jonasmf.auctionengine.service.RuntimeHealthTracker
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController()
class HealthController(
    private val runtimeHealthTracker: RuntimeHealthTracker,
) : HealthApi {
    private val logger = LoggerFactory.getLogger(HealthController::class.java)

    override suspend fun health(): ResponseEntity<Unit> {
        val snapshot = runtimeHealthTracker.snapshot()
        return if (snapshot.healthy) {
            ResponseEntity.noContent().build()
        } else {
            logger.warn("Health check failed: {}", snapshot.reason ?: "runtime unhealthy")
            ResponseEntity.status(503).build()
        }
    }
}
