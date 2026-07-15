package net.jonasmf.auctionengine.schedules

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class BackgroundWorkLauncher(
    private val backgroundWorkScope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(BackgroundWorkLauncher::class.java)

    fun launchSingleFlight(
        running: AtomicBoolean,
        taskName: String,
        block: () -> Unit,
    ): Boolean {
        if (!running.compareAndSet(false, true)) {
            log.info("Skipping {} because a run is already in progress.", taskName)
            return false
        }

        val job =
            backgroundWorkScope.launch {
                try {
                    block()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.error("Background task {} failed.", taskName, error)
                }
            }
        job.invokeOnCompletion {
            running.set(false)
        }
        return true
    }
}
