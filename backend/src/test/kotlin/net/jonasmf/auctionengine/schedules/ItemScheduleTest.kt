package net.jonasmf.auctionengine.schedules

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.repository.rds.ItemPersistenceSummary
import net.jonasmf.auctionengine.service.ItemSyncResult
import net.jonasmf.auctionengine.service.ItemSyncService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ItemScheduleTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe, Region.Taiwan),
        )
    @Test
    fun `syncItems skips and logs when sync already running`() {
        val service = mockk<ItemSyncService>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val listAppender = attachAppender(BackgroundWorkLauncher::class.java)

        every { service.syncConfiguredStaticDataRegion() } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            syncResult()
        }

        try {
            val schedule = ItemSchedule(properties, service, immediateBackgroundWorkLauncher(), true)
            val future = executor.submit<Unit> { schedule.syncItems() }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            schedule.syncItems()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(messages.any { it.contains("Skipping item-sync because a run is already in progress.") })
            verify(exactly = 1) { service.syncConfiguredStaticDataRegion() }

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
        val service = mockk<ItemSyncService>()
        val listAppender = attachAppender()
        val schedule = ItemSchedule(properties, service, immediateBackgroundWorkLauncher(), false)

        try {
            schedule.syncItemsOnSchedule()

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(
                messages.any {
                    it.contains(
                        "Skipping scheduled item sync because static data sync is disabled for this deployment.",
                    )
                },
            )
            verify(exactly = 0) { service.syncConfiguredStaticDataRegion() }
        } finally {
            detachAppender(listAppender)
        }
    }

    private fun syncResult() =
        ItemSyncResult(
            region = Region.Europe,
            auctionSourceCount = 0,
            recipeCraftedSourceCount = 0,
            recipeReagentSourceCount = 0,
            candidateItemCount = 0,
            existingItemCount = 0,
            missingItemCount = 0,
            skippedByBackoffCount = 0,
            skippedManualDisabledCount = 0,
            fetchedItemCount = 0,
            itemFetchFailures = 0,
            persistedItemCount = 0,
            persistenceSummary =
                ItemPersistenceSummary(
                    localesUpserted = 0,
                    itemQualitiesUpserted = 0,
                    inventoryTypesUpserted = 0,
                    itemBindingsUpserted = 0,
                    itemClassesUpserted = 0,
                    itemSubclassesUpserted = 0,
                    itemAppearanceReferencesUpserted = 0,
                    itemsUpserted = 0,
                    itemAppearanceLinksUpserted = 0,
                ),
            durationMs = 1,
        )

    private fun attachAppender(
        loggerClass: Class<*> = ItemSchedule::class.java,
    ): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(loggerClass) as Logger
        return ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    private fun detachAppender(
        appender: ListAppender<ILoggingEvent>,
        loggerClass: Class<*> = ItemSchedule::class.java,
    ) {
        val logger = LoggerFactory.getLogger(loggerClass) as Logger
        logger.detachAppender(appender)
        appender.stop()
    }
}
