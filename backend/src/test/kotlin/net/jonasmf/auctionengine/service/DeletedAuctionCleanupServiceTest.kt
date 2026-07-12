package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.repository.rds.AuctionRepository
import net.jonasmf.auctionengine.repository.rds.AuctionStatsHourlyJDBCRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class DeletedAuctionCleanupServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var service: DeletedAuctionCleanupService

    @Autowired
    lateinit var auctionHouseService: AuctionHouseService

    @Autowired
    lateinit var auctionRepository: AuctionRepository

    @Autowired
    lateinit var auctionStatsDailyRepository: AuctionStatsHourlyJDBCRepository

    @Autowired
    lateinit var auctionStatsHourlyRepository: AuctionRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var blizzardAuctionService: BlizzardAuctionService

    @BeforeEach
    fun beforeEach() {
        val today = Instant.now()
        val yesterday = Instant.now().minus(Duration.ofDays(1))
        auctionHouseService.createIfMissing(
            ConnectedRealm(
                id = 1,
                auctionHouse =
                    AuctionHouse(
                        lastAuctionPriceDeleteEvent = today,
                        lastHistoryDeleteEvent = today,
                        lastHistoryDeleteEventDaily = today,
                    ),
            ),
        )
        auctionHouseService.createIfMissing(
            ConnectedRealm(
                id = 2,
                auctionHouse =
                    AuctionHouse(
                        nextUpdate = yesterday,
                        lastModified = yesterday,
                        lastAuctionPriceDeleteEvent = yesterday,
                        lastHistoryDeleteEvent = yesterday,
                        lastHistoryDeleteEventDaily = yesterday,
                        region = Region.Europe,
                    ),
                realms =
                    mutableListOf(
                        Realm(
                            id = 1,
                            region =
                                RegionDBO(
                                    1,
                                    name = "Europe",
                                    type = Region.Europe,
                                ),
                            name = "Test realm",
                            category = TODO(),
                            locale = TODO(),
                            timezone = TODO(),
                            gameBuild = TODO(),
                            slug = TODO(),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun `Should successfully delete daily stats and update only for old data`() {
        /*jdbcTemplate.update(
            """
            INSERT INTO auction (id, connected_realm_id, update_history_id,)
            VALUES (
                'asdsad'
            )
            """.trimIndent(),
        )
        val result = auctionStatsDailyRepository.getForConnectedRealm(2)
         */

        val ah = auctionHouseService.getReadyForUpdate(Region.Europe)
        blizzardAuctionService.updateAuctionHouses(Region.Europe, ah)
        assertEquals(1, 2)
    }
}
