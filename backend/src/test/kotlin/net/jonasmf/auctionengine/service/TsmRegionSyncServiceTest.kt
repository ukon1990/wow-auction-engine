package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.constant.TsmSubjectType
import net.jonasmf.auctionengine.integration.tsm.TsmPublicDataClient
import net.jonasmf.auctionengine.integration.tsm.TsmRegionCsvRow
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.rds.TsmRegionMetricRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.time.toKotlinInstant
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse as AuctionHouseDbo

class TsmRegionSyncServiceTest {
    private val zone = ZoneOffset.ofHours(1)
    private val now = Instant.parse("2026-07-14T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe),
        )

    @Test
    fun `shouldSyncRegion returns true when last sync is null`() {
        val service = service()

        assertThat(service.shouldSyncRegion(null, zone)).isTrue()
    }

    @Test
    fun `shouldSyncRegion returns false when last sync is same day in schedule zone`() {
        val service = service()
        // 2026-07-14 09:00 GMT+1 == 2026-07-14 08:00Z — same calendar day as now in GMT+1
        val sameDay = Instant.parse("2026-07-14T08:00:00Z")

        assertThat(service.shouldSyncRegion(sameDay, zone)).isFalse()
    }

    @Test
    fun `shouldSyncRegion returns true when last sync was previous day in schedule zone`() {
        val service = service()
        // 2026-07-13 23:30 GMT+1 == 2026-07-13 22:30Z — previous day vs 2026-07-14 in GMT+1
        val previousDay = Instant.parse("2026-07-13T22:30:00Z")

        assertThat(service.shouldSyncRegion(previousDay, zone)).isTrue()
    }

    @Test
    fun `syncRegion skips download when already synced today`() {
        val auctionHouseRepository = mockk<AuctionHouseRepository>()
        val tsmPublicDataClient = mockk<TsmPublicDataClient>()
        val auctionHouseService = mockk<AuctionHouseService>()
        every { auctionHouseRepository.findByConnectedId(CommodityRealms.idFor(Region.Europe)) } returns
            Optional.of(
                AuctionHouseDbo(
                    connectedId = CommodityRealms.idFor(Region.Europe),
                    region = Region.Europe,
                    lastTsmRegionSync = Instant.parse("2026-07-14T06:00:00Z"),
                ),
            )

        val service =
            service(
                auctionHouseRepository = auctionHouseRepository,
                tsmPublicDataClient = tsmPublicDataClient,
                auctionHouseService = auctionHouseService,
            )

        service.syncRegion(Region.Europe, zone)

        verify(exactly = 0) { tsmPublicDataClient.downloadItems(any()) }
        verify(exactly = 0) { tsmPublicDataClient.downloadPets(any()) }
        verify(exactly = 0) { auctionHouseService.updateLastTsmRegionSync(any(), any()) }
    }

    @Test
    fun `syncRegion upserts items and pets then updates marker on full success`() {
        val auctionHouseRepository = mockk<AuctionHouseRepository>()
        val tsmPublicDataClient = mockk<TsmPublicDataClient>()
        val tsmRegionMetricRepository = mockk<TsmRegionMetricRepository>()
        val auctionHouseService = mockk<AuctionHouseService>()

        every { auctionHouseRepository.findByConnectedId(CommodityRealms.idFor(Region.Europe)) } returns Optional.empty()
        every { tsmPublicDataClient.downloadItems(Region.Europe) } returns listOf(csvRow(39, "0.25"))
        every { tsmPublicDataClient.downloadPets(Region.Europe) } returns listOf(csvRow(39, "0.50"))
        every { tsmRegionMetricRepository.upsertAll(any()) } returns 2
        every {
            auctionHouseService.updateLastTsmRegionSync(
                CommodityRealms.idFor(Region.Europe),
                now.toKotlinInstant(),
            )
        } returns 1

        val service =
            service(
                auctionHouseRepository = auctionHouseRepository,
                tsmPublicDataClient = tsmPublicDataClient,
                tsmRegionMetricRepository = tsmRegionMetricRepository,
                auctionHouseService = auctionHouseService,
            )

        service.syncRegion(Region.Europe, zone)

        verify {
            tsmRegionMetricRepository.upsertAll(
                match { metrics ->
                    metrics.size == 2 &&
                        metrics.any {
                            it.subjectType == TsmSubjectType.ITEM &&
                                it.subjectId == 39 &&
                                it.saleRate.compareTo(BigDecimal("0.25")) == 0
                        } &&
                        metrics.any {
                            it.subjectType == TsmSubjectType.PET &&
                                it.subjectId == 39 &&
                                it.saleRate.compareTo(BigDecimal("0.50")) == 0
                        }
                },
            )
        }
        verify(exactly = 1) {
            auctionHouseService.updateLastTsmRegionSync(
                CommodityRealms.idFor(Region.Europe),
                now.toKotlinInstant(),
            )
        }
    }

    @Test
    fun `syncRegion does not update marker when pets download fails`() {
        val auctionHouseRepository = mockk<AuctionHouseRepository>()
        val tsmPublicDataClient = mockk<TsmPublicDataClient>()
        val tsmRegionMetricRepository = mockk<TsmRegionMetricRepository>()
        val auctionHouseService = mockk<AuctionHouseService>()

        every { auctionHouseRepository.findByConnectedId(CommodityRealms.idFor(Region.Europe)) } returns Optional.empty()
        every { tsmPublicDataClient.downloadItems(Region.Europe) } returns listOf(csvRow(39, "0.25"))
        every { tsmPublicDataClient.downloadPets(Region.Europe) } throws IllegalStateException("pets failed")

        val service =
            service(
                auctionHouseRepository = auctionHouseRepository,
                tsmPublicDataClient = tsmPublicDataClient,
                tsmRegionMetricRepository = tsmRegionMetricRepository,
                auctionHouseService = auctionHouseService,
            )

        service.syncRegion(Region.Europe, zone)

        verify(exactly = 0) { tsmRegionMetricRepository.upsertAll(any()) }
        verify(exactly = 0) { auctionHouseService.updateLastTsmRegionSync(any(), any()) }
    }

    private fun service(
        auctionHouseRepository: AuctionHouseRepository = mockk(),
        tsmPublicDataClient: TsmPublicDataClient = mockk(),
        tsmRegionMetricRepository: TsmRegionMetricRepository = mockk(),
        auctionHouseService: AuctionHouseService = mockk(),
    ) = TsmRegionSyncService(
        properties = properties,
        tsmPublicDataClient = tsmPublicDataClient,
        tsmRegionMetricRepository = tsmRegionMetricRepository,
        auctionHouseRepository = auctionHouseRepository,
        auctionHouseService = auctionHouseService,
        clock = clock,
        scheduleZone = "GMT+1",
    )

    private fun csvRow(
        subjectId: Int,
        saleRate: String,
    ) = TsmRegionCsvRow(
        subjectId = subjectId,
        saleRate = BigDecimal(saleRate),
        soldPerDay = BigDecimal("1.0"),
        marketValue = 1000L,
        historical = 2000L,
        avgSalePrice = 1500L,
        sourceUpdatedAt = Instant.parse("2026-07-14T00:00:00Z"),
    )
}
