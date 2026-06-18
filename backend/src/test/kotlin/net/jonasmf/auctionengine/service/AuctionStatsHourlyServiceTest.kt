package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionItemDTO
import net.jonasmf.auctionengine.repository.rds.AuctionStatsHourlyJDBCRepository
import net.jonasmf.auctionengine.repository.rds.HourlyStatsUpsertRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class AuctionStatsHourlyServiceTest {
    @Test
    fun `processHourlyPriceStatistics uses connected realm local date and hour`() {
        val repository = mockk<AuctionStatsHourlyJDBCRepository>()
        val rows = slot<List<HourlyStatsUpsertRow>>()
        val hour = slot<Int>()
        every { repository.upsertHour(capture(rows), capture(hour)) } returns 1
        val service = AuctionStatsHourlyService(repository)
        val connectedRealm = connectedRealm(timezone = "Pacific/Auckland")

        service.updateHourlyStatsForRealm(
            connectedRealm = connectedRealm,
            auctions = listOf(auction()),
            lastModified = ZonedDateTime.of(2026, 1, 1, 11, 15, 0, 0, ZoneOffset.UTC),
        )

        assertEquals(0, hour.captured)
        assertEquals(LocalDate.of(2026, 1, 2), rows.captured.single().date)
    }

    private fun connectedRealm(timezone: String) =
        ConnectedRealm(
            id = 1,
            auctionHouse = AuctionHouse(id = 1, connectedId = 1, region = Region.Europe),
            realms =
                mutableListOf(
                    Realm(
                        id = 1,
                        region = RegionDBO(id = 1, name = "Europe", type = Region.Europe),
                        name = "Realm 1",
                        category = "Normal",
                        locale = Locale.EN_GB,
                        timezone = timezone,
                        gameBuild = GameBuildVersion.RETAIL,
                        slug = "realm-1",
                    ),
                ),
        )

    private fun auction() =
        AuctionDTO(
            id = 1,
            item = AuctionItemDTO(id = 19019),
            quantity = 2,
            unit_price = null,
            buyout = 100,
            time_left = AuctionTimeLeft.LONG,
        )
}
