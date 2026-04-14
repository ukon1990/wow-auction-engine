package net.jonasmf.auctionengine.schedules

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.config.BucketConfig
import net.jonasmf.auctionengine.config.WaeS3Properties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncResult
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProfessionRecipeScheduleTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe, Region.Taiwan),
        )
    private val s3Properties =
        WaeS3Properties(
            buckets =
                mapOf(
                    "europe" to BucketConfig("wah-data-eu", "eu-west-1"),
                    "northamerica" to BucketConfig("wah-data-us", "us-west-1"),
                    "korea" to BucketConfig("wah-data-as", "ap-northeast-2"),
                    "taiwan" to BucketConfig("wah-data-as", "ap-northeast-2"),
                ),
        )

    @Test
    fun `syncProfessionRecipes skips and logs when sync already running`() {
        val service = mockk<ProfessionRecipeSyncService>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val listAppender = attachAppender()

        every { service.syncConfiguredStaticDataRegion() } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            mockk<ProfessionRecipeSyncResult>(relaxed = true)
        }

        try {
            val schedule = ProfessionRecipeSchedule(properties, s3Properties, service, "eu-west-1")
            val future = executor.submit<Unit> { schedule.syncProfessionRecipes() }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            schedule.syncProfessionRecipes()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(
                messages.any { it.contains("Skipping manual profession/recipe sync because sync already running.") },
            )
            verify(exactly = 1) { service.syncConfiguredStaticDataRegion() }

            release.countDown()
            future.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
            detachAppender(listAppender)
        }
    }

    @Test
    fun `syncProfessionRecipes clears guard after success`() {
        val service = mockk<ProfessionRecipeSyncService>()
        every { service.syncConfiguredStaticDataRegion() } returns mockk<ProfessionRecipeSyncResult>(relaxed = true)

        val schedule = ProfessionRecipeSchedule(properties, s3Properties, service, "eu-west-1")

        schedule.syncProfessionRecipes()
        schedule.syncProfessionRecipes()

        verify(exactly = 2) { service.syncConfiguredStaticDataRegion() }
    }

    @Test
    fun `syncProfessionRecipes clears guard after exception`() {
        val service = mockk<ProfessionRecipeSyncService>()
        every { service.syncConfiguredStaticDataRegion() } throws RuntimeException("boom") andThen
            mockk<ProfessionRecipeSyncResult>(relaxed = true)

        val schedule = ProfessionRecipeSchedule(properties, s3Properties, service, "eu-west-1")

        runCatching { schedule.syncProfessionRecipes() }
        schedule.syncProfessionRecipes()

        verify(exactly = 2) { service.syncConfiguredStaticDataRegion() }
    }

    @Test
    fun `scheduled sync skips when deployment region does not match static data region`() {
        val service = mockk<ProfessionRecipeSyncService>()
        val listAppender = attachAppender()
        val schedule = ProfessionRecipeSchedule(properties, s3Properties, service, "us-west-1")

        try {
            schedule.syncProfessionRecipesOnSchedule()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(
                messages.any {
                    it.contains(
                        "Skipping scheduled profession/recipe sync because deployment AWS region us-west-1 does not match static data region Europe bucket region eu-west-1.",
                    )
                },
            )
            verify(exactly = 0) { service.syncConfiguredStaticDataRegion() }
        } finally {
            detachAppender(listAppender)
        }
    }

    private fun attachAppender(): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(ProfessionRecipeSchedule::class.java) as Logger
        return ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    private fun detachAppender(appender: ListAppender<ILoggingEvent>) {
        val logger = LoggerFactory.getLogger(ProfessionRecipeSchedule::class.java) as Logger
        logger.detachAppender(appender)
        appender.stop()
    }
}
