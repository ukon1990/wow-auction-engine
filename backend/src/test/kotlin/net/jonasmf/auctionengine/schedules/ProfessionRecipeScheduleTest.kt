package net.jonasmf.auctionengine.schedules

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncResult
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncGuard
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
    @Test
    fun `syncProfessionRecipes skips and logs when sync already running`() {
        val service = mockk<ProfessionRecipeSyncService>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val listAppender = attachAppender()

        every { service.syncConfiguredStaticDataRegion(any()) } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            mockk<ProfessionRecipeSyncResult>(relaxed = true)
        }

        try {
            val schedule = ProfessionRecipeSchedule(properties, service, createGuard(), true)
            val future = executor.submit<Unit> { schedule.syncProfessionRecipes() }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            schedule.syncProfessionRecipes()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(
                messages.any { it.contains("Skipping manual profession/recipe sync because sync already running.") },
            )
            verify(exactly = 1) { service.syncConfiguredStaticDataRegion(any()) }

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
        every { service.syncConfiguredStaticDataRegion(any()) } returns mockk<ProfessionRecipeSyncResult>(relaxed = true)

        val schedule = ProfessionRecipeSchedule(properties, service, createGuard(), true)

        schedule.syncProfessionRecipes()
        schedule.syncProfessionRecipes()

        verify(exactly = 2) { service.syncConfiguredStaticDataRegion(any()) }
    }

    @Test
    fun `syncProfessionRecipes clears guard after exception`() {
        val service = mockk<ProfessionRecipeSyncService>()
        every { service.syncConfiguredStaticDataRegion(any()) } throws RuntimeException("boom") andThen
            mockk<ProfessionRecipeSyncResult>(relaxed = true)

        val schedule = ProfessionRecipeSchedule(properties, service, createGuard(), true)

        runCatching { schedule.syncProfessionRecipes() }
        schedule.syncProfessionRecipes()

        verify(exactly = 2) { service.syncConfiguredStaticDataRegion(any()) }
    }

    @Test
    fun `scheduled sync skips when static data sync is disabled`() {
        val service = mockk<ProfessionRecipeSyncService>()
        val listAppender = attachAppender()
        val schedule = ProfessionRecipeSchedule(properties, service, createGuard(), false)

        try {
            schedule.syncProfessionRecipesOnSchedule()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(
                messages.any {
                    it.contains(
                        "Skipping scheduled profession/recipe sync because static data sync is disabled for this deployment.",
                    )
                },
            )
            verify(exactly = 0) { service.syncConfiguredStaticDataRegion(any()) }
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

    private fun createGuard(): ProfessionRecipeSyncGuard {
        val guard = mockk<ProfessionRecipeSyncGuard>()
        val held = java.util.concurrent.atomic.AtomicBoolean(false)
        every { guard.tryAcquire() } answers {
            if (held.compareAndSet(false, true)) mockk(relaxed = true) else null
        }
        every { guard.release(any()) } answers { held.set(false) }
        return guard
    }
}
