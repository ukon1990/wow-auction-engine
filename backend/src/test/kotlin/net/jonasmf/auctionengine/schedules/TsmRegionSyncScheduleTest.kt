package net.jonasmf.auctionengine.schedules

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.service.TsmRegionSyncService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TsmRegionSyncScheduleTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe, Region.Taiwan),
        )

    @Test
    fun `syncTsmRegion skips and logs when sync already running`() {
        val service = mockk<TsmRegionSyncService>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val listAppender = attachAppender(BackgroundWorkLauncher::class.java)

        every { service.syncConfiguredRegions() } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
        }

        try {
            val schedule = TsmRegionSyncSchedule(properties, service, immediateBackgroundWorkLauncher(), true)
            val future = executor.submit<Unit> { schedule.syncTsmRegion() }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            schedule.syncTsmRegion()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(messages.any { it.contains("Skipping tsm-region-sync because a run is already in progress.") })
            verify(exactly = 1) { service.syncConfiguredRegions() }

            release.countDown()
            future.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
            detachAppender(listAppender, BackgroundWorkLauncher::class.java)
        }
    }

    @Test
    fun `scheduled sync skips when static data sync is disabled`() {
        val service = mockk<TsmRegionSyncService>()
        val listAppender = attachAppender()
        val schedule = TsmRegionSyncSchedule(properties, service, immediateBackgroundWorkLauncher(), false)

        try {
            schedule.syncTsmRegionOnSchedule()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(
                messages.any {
                    it.contains(
                        "Skipping scheduled TSM region sync because static data sync is disabled for this deployment.",
                    )
                },
            )
            verify(exactly = 0) { service.syncConfiguredRegions() }
        } finally {
            detachAppender(listAppender)
        }
    }

    @Test
    fun `manual sync invokes service when enabled`() {
        val service = mockk<TsmRegionSyncService>()
        every { service.syncConfiguredRegions() } just runs

        val schedule = TsmRegionSyncSchedule(properties, service, immediateBackgroundWorkLauncher(), true)
        schedule.syncTsmRegion()

        verify(exactly = 1) { service.syncConfiguredRegions() }
    }

    private fun attachAppender(
        loggerClass: Class<*> = TsmRegionSyncSchedule::class.java,
    ): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(loggerClass) as Logger
        return ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    private fun detachAppender(
        appender: ListAppender<ILoggingEvent>,
        loggerClass: Class<*> = TsmRegionSyncSchedule::class.java,
    ) {
        val logger = LoggerFactory.getLogger(loggerClass) as Logger
        logger.detachAppender(appender)
        appender.stop()
    }
}
