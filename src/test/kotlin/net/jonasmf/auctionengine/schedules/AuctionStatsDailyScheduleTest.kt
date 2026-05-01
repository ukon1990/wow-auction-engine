package net.jonasmf.auctionengine.schedules

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.RealmDynamo
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.service.AuctionHouseService
import net.jonasmf.auctionengine.service.AuctionStatsDailyService
import net.jonasmf.auctionengine.service.AuctionStatsDailyUpdateResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class AuctionStatsDailyScheduleTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe, Region.Taiwan),
        )

    @Test
    fun `updateDailyPriceStatistics uses timezone and starts null markers at 2026`() {
        val auctionHouseService = mockk<AuctionHouseService>()
        val auctionStatsDailyService = mockk<AuctionStatsDailyService>()
        val europeMarker = slot<Instant>()
        val taiwanMarker = slot<Instant>()
        val europeHouse =
            AuctionHouse(
                id = 1,
                connectedId = 1,
                region = Region.Europe,
                lastDailyPriceUpdate = Instant.parse("2026-01-01T11:00:00Z"),
                realms = listOf(RealmDynamo(timezone = "Pacific/Auckland")),
            )
        val taiwanHouse =
            AuctionHouse(
                id = 2,
                connectedId = 2,
                region = Region.Taiwan,
                lastDailyPriceUpdate = null,
                realms = listOf(RealmDynamo(timezone = "Asia/Taipei")),
            )

        every { auctionHouseService.findAllByRegion(Region.Europe) } returns listOf(europeHouse)
        every { auctionHouseService.findAllByRegion(Region.Taiwan) } returns listOf(taiwanHouse)
        every { auctionStatsDailyService.updateForDate(any(), any(), any()) } returns
            AuctionStatsDailyUpdateResult(listOf(LocalDate.of(2026, 1, 3)), updatedRows = 7)
        every { auctionHouseService.updateLastDailyPriceUpdate(1, capture(europeMarker)) } returns 1
        every { auctionHouseService.updateLastDailyPriceUpdate(2, capture(taiwanMarker)) } returns 1

        AuctionStatsDailySchedule(properties, auctionHouseService, auctionStatsDailyService)
            .updateDailyPriceStatistics()

        verify(exactly = 1) {
            auctionStatsDailyService.updateForDate(
                connectedRealmId = 1,
                lastUpdated = LocalDate.of(2026, 1, 2),
                endDate = any(),
            )
        }
        verify(exactly = 1) {
            auctionStatsDailyService.updateForDate(
                connectedRealmId = 2,
                lastUpdated = LocalDate.of(2025, 12, 31),
                endDate = any(),
            )
        }
        assertEquals(
            LocalDate.of(2026, 1, 3),
            europeMarker.captured
                .toJavaInstant()
                .atZone(ZoneId.of("Pacific/Auckland"))
                .toLocalDate(),
        )
        assertEquals(
            LocalDate.of(2026, 1, 3),
            taiwanMarker.captured
                .toJavaInstant()
                .atZone(ZoneId.of("Asia/Taipei"))
                .toLocalDate(),
        )
    }

    @Test
    fun `updateDailyPriceStatistics skips concurrent run and clears guard`() {
        val auctionHouseService = mockk<AuctionHouseService>()
        val auctionStatsDailyService = mockk<AuctionStatsDailyService>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val house = AuctionHouse(id = 1, connectedId = 1, region = Region.Europe)

        every { auctionHouseService.findAllByRegion(Region.Europe) } returns listOf(house)
        every { auctionHouseService.findAllByRegion(Region.Taiwan) } returns emptyList()
        every { auctionStatsDailyService.updateForDate(any(), any(), any()) } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            AuctionStatsDailyUpdateResult(listOf(LocalDate.of(2026, 1, 2)), updatedRows = 1)
        } andThen AuctionStatsDailyUpdateResult(listOf(LocalDate.of(2026, 1, 3)), updatedRows = 1)
        every { auctionHouseService.updateLastDailyPriceUpdate(any(), any()) } returns 1

        val schedule = AuctionStatsDailySchedule(properties, auctionHouseService, auctionStatsDailyService)
        try {
            val future = executor.submit<Unit> { schedule.updateDailyPriceStatistics() }
            started.await(5, TimeUnit.SECONDS)

            schedule.updateDailyPriceStatistics()

            verify(exactly = 1) { auctionStatsDailyService.updateForDate(any(), any(), any()) }
            release.countDown()
            future.get(5, TimeUnit.SECONDS)

            schedule.updateDailyPriceStatistics()

            verify(exactly = 2) { auctionStatsDailyService.updateForDate(any(), any(), any()) }
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `updateDailyPriceStatistics does not update marker when house update fails`() {
        val auctionHouseService = mockk<AuctionHouseService>()
        val auctionStatsDailyService = mockk<AuctionStatsDailyService>()
        val house = AuctionHouse(id = 1, connectedId = 1, region = Region.Europe)

        every { auctionHouseService.findAllByRegion(Region.Europe) } returns listOf(house)
        every { auctionHouseService.findAllByRegion(Region.Taiwan) } returns emptyList()
        every { auctionStatsDailyService.updateForDate(any(), any(), any()) } throws RuntimeException("boom")

        AuctionStatsDailySchedule(properties, auctionHouseService, auctionStatsDailyService)
            .updateDailyPriceStatistics()

        verify(exactly = 0) { auctionHouseService.updateLastDailyPriceUpdate(any(), any()) }
    }
}
