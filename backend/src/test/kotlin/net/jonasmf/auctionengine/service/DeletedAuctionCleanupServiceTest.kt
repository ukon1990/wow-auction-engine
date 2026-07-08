package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.repository.rds.AuctionRepository
import net.jonasmf.auctionengine.repository.rds.AuctionStatsHourlyJDBCRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.Instant

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
                        lastAuctionPriceDeleteEvent = yesterday,
                        lastHistoryDeleteEvent = yesterday,
                        lastHistoryDeleteEventDaily = yesterday,
                    ),
            ),
        )
    }

    @Nested
    sealed class CleanupOfDailyAuctionStatistics {
        @Test
        fun `Should successfully delete daily stats and update only for old data`() {
            val result = auctionStatsDailyRepository.getForConnectedRealm(2)
        }
    }
}
