package net.jonasmf.auctionengine

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class HealthResponse(
    val status: String = "UP",
    val checkedAt: Instant = Instant.now()
)

@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): ResponseEntity<HealthResponse> = ResponseEntity.ok(HealthResponse())
}
