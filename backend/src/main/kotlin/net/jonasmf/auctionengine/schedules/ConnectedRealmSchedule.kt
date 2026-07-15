package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.service.AuthService
import net.jonasmf.auctionengine.service.ConnectedRealmService
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
@ConditionalOnProperty(name = ["app.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class ConnectedRealmSchedule (
    private val authService: AuthService,
    private val connectedRealmService: ConnectedRealmService,
    private val backgroundWorkLauncher: BackgroundWorkLauncher,
) : ApplicationRunner {
    val log: Logger = LoggerFactory.getLogger(ConnectedRealmSchedule::class.java.name)
    private val seededAt: Instant = Instant.EPOCH
    private val syncInProgress = AtomicBoolean(false)

    @Scheduled(
        fixedDelayString = "PT1H",
        initialDelayString = "\${app.scheduling.initial-delay:PT30S}",
    )
    fun updateRealms() {
        backgroundWorkLauncher.launchSingleFlight(syncInProgress, "connected-realm-update") {
            try {
                connectedRealmService.updateRealms()
            } catch (error: Exception) {
                log.warn("Connected realm update failed: ${error.localizedMessage}")
            }
        }
    }

    override fun run(args: ApplicationArguments) {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.info("Skipping connected realm startup synchronization because an update is already running.")
            return
        }

        try {
            runCatching {
                authService.ensureToken().block()
                connectedRealmService.updateRealms()
            }.onFailure { error ->
                log.warn(
                    "Connected realm startup synchronization failed. Continuing application startup: ${error.localizedMessage}",
                )
            }
        } finally {
            syncInProgress.set(false)
        }
    }
}
