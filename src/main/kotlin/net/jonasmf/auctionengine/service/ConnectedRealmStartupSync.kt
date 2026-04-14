package net.jonasmf.auctionengine.service

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class ConnectedRealmStartupSync(
    private val authService: AuthService,
    private val connectedRealmService: ConnectedRealmService,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(ConnectedRealmStartupSync::class.java)

    override fun run(args: ApplicationArguments) {
        runCatching {
            authService.ensureToken().block()
            connectedRealmService.updateRealms()
        }.onFailure {
            log.error(
                "Connected realm startup synchronization failed. Continuing application startup.",
                it,
            )
        }
    }
}
