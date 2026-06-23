package net.jonasmf.auctionengine.schedules

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.service.BlizzardMediaBackfillResult
import net.jonasmf.auctionengine.service.BlizzardMediaBackfillService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BlizzardMediaBackfillScheduleTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe, Region.Taiwan),
        )
    @Test
    fun `backfillMedia skips and logs when backfill already running`() {
        val service = mockk<BlizzardMediaBackfillService>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val listAppender = attachAppender()

        every { service.backfillConfiguredStaticDataRegion() } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            backfillResult()
        }

        try {
            val schedule = BlizzardMediaBackfillSchedule(properties, service, true)
            val future = executor.submit<Unit> { schedule.backfillMedia() }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            schedule.backfillMedia()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(messages.any { it.contains("Skipping manual media backfill because backfill already running.") })
            verify(exactly = 1) { service.backfillConfiguredStaticDataRegion() }

            release.countDown()
            future.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
            detachAppender(listAppender)
        }
    }

    @Test
    fun `scheduled backfill skips when static data sync is disabled`() {
        val service = mockk<BlizzardMediaBackfillService>()
        val listAppender = attachAppender()
        val schedule = BlizzardMediaBackfillSchedule(properties, service, false)

        try {
            schedule.backfillMediaOnSchedule()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(
                messages.any {
                    it.contains(
                        "Skipping scheduled media backfill because static data sync is disabled for this deployment.",
                    )
                },
            )
            verify(exactly = 0) { service.backfillConfiguredStaticDataRegion() }
        } finally {
            detachAppender(listAppender)
        }
    }

    private fun backfillResult() =
        BlizzardMediaBackfillResult(
            region = Region.Europe,
            itemUpdates = 1,
            itemAppearanceUpdates = 2,
            recipeUpdates = 3,
            professionUpdates = 4,
        )

    private fun attachAppender(): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(BlizzardMediaBackfillSchedule::class.java) as Logger
        return ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    private fun detachAppender(appender: ListAppender<ILoggingEvent>) {
        val logger = LoggerFactory.getLogger(BlizzardMediaBackfillSchedule::class.java) as Logger
        logger.detachAppender(appender)
        appender.stop()
    }
}
