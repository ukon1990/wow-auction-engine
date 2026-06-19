package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.repository.rds.AuctionStatsHourlyJDBCRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class AuctionStatsHourlyServiceTest {
    @Test
    fun `updateHourlyStatsForRealm uses connected realm local hour and update history id`() {
        val repository = mockk<AuctionStatsHourlyJDBCRepository>()
        val hour = slot<Int>()
        val updateHistoryId = slot<Long>()
        every { repository.updateHourlyStats(capture(hour), capture(updateHistoryId)) } returns 1
        val service = AuctionStatsHourlyService(repository)
        val connectedRealm = connectedRealm(timezone = "Pacific/Auckland")

        val result =
            service.updateHourlyStatsForRealm(
                connectedRealm = connectedRealm,
                lastModified = ZonedDateTime.of(2026, 1, 1, 11, 15, 0, 0, ZoneOffset.UTC),
                connectedRealmUpdateHistoryId = 42,
            )

        assertEquals(0, hour.captured)
        assertEquals(42, updateHistoryId.captured)
        assertEquals(1, result.insertedRows)
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
}
