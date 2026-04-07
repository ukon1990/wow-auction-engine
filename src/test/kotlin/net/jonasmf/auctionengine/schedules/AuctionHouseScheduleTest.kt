package net.jonasmf.auctionengine.schedules

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.service.AuctionHouseService
import net.jonasmf.auctionengine.service.BlizzardAuctionService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AuctionHouseScheduleTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            region = Region.Europe,
        )

    @Test
    fun `checkForUpdates skips and logs when a batch is already running`() {
        val blizzardAuctionService = mockk<BlizzardAuctionService>()
        val auctionHouseService = mockk<AuctionHouseService>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val listAppender = attachAppender()

        every { auctionHouseService.getReadyForUpdate(Region.Europe) } returns
            listOf(
                AuctionHouseDynamo(connectedId = 1, region = Region.Europe),
            )
        every { blizzardAuctionService.updateAuctionHouses(any()) } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
        }

        try {
            val schedule = AuctionHouseSchedule(properties, blizzardAuctionService, auctionHouseService)
            val future = executor.submit<Unit> { schedule.checkForUpdates() }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            schedule.checkForUpdates()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(
                messages.any {
                    it.contains(
                        "Skipping scheduled auction house update check because an update batch is already running.",
                    )
                },
            )
            verify(exactly = 1) { blizzardAuctionService.updateAuctionHouses(any()) }

            release.countDown()
            future.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
            detachAppender(listAppender)
        }
    }

    @Test
    fun `checkForUpdates clears running guard after successful completion`() {
        val blizzardAuctionService = mockk<BlizzardAuctionService>()
        val auctionHouseService = mockk<AuctionHouseService>()
        every { auctionHouseService.getReadyForUpdate(Region.Europe) } returns
            listOf(
                AuctionHouseDynamo(connectedId = 1, region = Region.Europe),
            )
        every { blizzardAuctionService.updateAuctionHouses(any()) } returns Unit

        val schedule = AuctionHouseSchedule(properties, blizzardAuctionService, auctionHouseService)

        schedule.checkForUpdates()
        schedule.checkForUpdates()

        verify(exactly = 2) { blizzardAuctionService.updateAuctionHouses(any()) }
    }

    @Test
    fun `checkForUpdates clears running guard after exception`() {
        val blizzardAuctionService = mockk<BlizzardAuctionService>()
        val auctionHouseService = mockk<AuctionHouseService>()
        every { auctionHouseService.getReadyForUpdate(Region.Europe) } returns
            listOf(
                AuctionHouseDynamo(connectedId = 1, region = Region.Europe),
            )
        every { blizzardAuctionService.updateAuctionHouses(any()) } throws RuntimeException("boom") andThen Unit

        val schedule = AuctionHouseSchedule(properties, blizzardAuctionService, auctionHouseService)

        runCatching { schedule.checkForUpdates() }
        schedule.checkForUpdates()

        verify(exactly = 2) { blizzardAuctionService.updateAuctionHouses(any()) }
    }

    private fun attachAppender(): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(AuctionHouseSchedule::class.java) as Logger
        return ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    private fun detachAppender(appender: ListAppender<ILoggingEvent>) {
        val logger = LoggerFactory.getLogger(AuctionHouseSchedule::class.java) as Logger
        logger.detachAppender(appender)
        appender.stop()
    }
}
