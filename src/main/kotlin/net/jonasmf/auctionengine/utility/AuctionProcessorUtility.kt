package net.jonasmf.auctionengine.utility

import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionStatsId
import net.jonasmf.auctionengine.dbo.rds.auction.DailyAuctionStats
import net.jonasmf.auctionengine.dbo.rds.auction.HourlyAuctionStats
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.repository.rds.DailyAuctionStatsRepository
import net.jonasmf.auctionengine.repository.rds.HourlyAuctionStatsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.Date

@Component
class AuctionProcessorUtility(
    private val dailyStatsRepo: DailyAuctionStatsRepository,
    private val hourlyStatsRepo: HourlyAuctionStatsRepository,
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(AuctionProcessorUtility::class.java)
    }

    /**
     * Process auction data and persist daily and hourly statistics.
     */
    fun processAuctions(
        auctions: List<AuctionDTO>,
        lastModified: Long,
        ahId: Int,
        ahTypeId: Int,
    ) {
        val start = System.currentTimeMillis()

        // Handle empty list case
        if (auctions.isEmpty()) {
            hourlyStatsRepo.saveAll(emptyList())
            dailyStatsRepo.saveAll(emptyList())
            LOG.info("Processed 0 auctions in ${System.currentTimeMillis() - start} ms")
            return
        }

        val map = mutableMapOf<String, AuctionItemStat>()

        // Process auctions into hourly stats
        val hourlyStats = mutableListOf<HourlyAuctionStats>()
        val connectedRealm = createDummyConnectedRealm(ahId)
        auctions.forEach { auctionDTO ->
            processHourlyStats(auctionDTO, Date(lastModified), hourlyStats, connectedRealm, ahTypeId)
        }
        hourlyStatsRepo.saveAll(hourlyStats)

        // Process auctions into daily stats
        val dailyStats = mutableListOf<DailyAuctionStats>()
        auctions.forEach { auctionDTO ->
            processDailyStats(auctionDTO, Date(lastModified), dailyStats, connectedRealm, ahTypeId)
        }
        dailyStatsRepo.saveAll(dailyStats)

        LOG.info("Processed ${auctions.size} auctions in ${System.currentTimeMillis() - start} ms")
    }

    /**
     * Process auction DTO to generate hourly statistics.
     */
    private fun processHourlyStats(
        auctionDTO: AuctionDTO,
        lastModified: Date,
        hourlyStats: MutableList<HourlyAuctionStats>,
        connectedRealm: ConnectedRealm,
        ahTypeId: Int,
    ) {
        val statsId =
            AuctionStatsId(
                connectedRealm = connectedRealm,
                gameBuildVersion = GameBuildVersion.RETAIL,
                itemId = auctionDTO.item.id,
                date = LocalDate.now(),
                petSpeciesId = auctionDTO.item.pet_species_id,
            )
        val hourlyStat =
            HourlyAuctionStats(
                id = statsId,
                price00 = auctionDTO.unit_price,
                quantity00 = auctionDTO.quantity,
                price01 = null,
                quantity01 = null,
                price02 = null,
                quantity02 = null,
                price03 = null,
                quantity03 = null,
                price04 = null,
                quantity04 = null,
                price05 = null,
                quantity05 = null,
                price06 = null,
                quantity06 = null,
                price07 = null,
                quantity07 = null,
                price08 = null,
                quantity08 = null,
                price09 = null,
                quantity09 = null,
                price10 = null,
                quantity10 = null,
                price11 = null,
                quantity11 = null,
                price12 = null,
                quantity12 = null,
                price13 = null,
                quantity13 = null,
                price14 = null,
                quantity14 = null,
                price15 = null,
                quantity15 = null,
                price16 = null,
                quantity16 = null,
                price17 = null,
                quantity17 = null,
                price18 = null,
                quantity18 = null,
                price19 = null,
                quantity19 = null,
                price20 = null,
                quantity20 = null,
                price21 = null,
                quantity21 = null,
                price22 = null,
                quantity22 = null,
                price23 = null,
                quantity23 = null,
            )
        hourlyStats.add(hourlyStat)
    }

    /**
     * Process auction DTO to generate daily statistics.
     */
    private fun processDailyStats(
        auctionDTO: AuctionDTO,
        lastModified: Date,
        dailyStats: MutableList<DailyAuctionStats>,
        connectedRealm: ConnectedRealm,
        ahTypeId: Int,
    ) {
        val statsId =
            AuctionStatsId(
                connectedRealm = connectedRealm,
                gameBuildVersion = GameBuildVersion.RETAIL,
                itemId = auctionDTO.item.id,
                date = LocalDate.now(),
                petSpeciesId = auctionDTO.item.pet_species_id,
            )
        val dailyStat =
            DailyAuctionStats(
                id = statsId,
            )
        dailyStats.add(dailyStat)
    }

    fun createDummyConnectedRealm(id: Int): ConnectedRealm =
        ConnectedRealm(
            id = id,
            auctionHouse =
                AuctionHouse(
                    id = null,
                    lastModified = null,
                    lastRequested = null,
                    nextUpdate = java.time.ZonedDateTime.now(),
                    lowestDelay = 0L,
                    averageDelay = 60,
                    highestDelay = 0L,
                    tsmFile = null,
                    statsFile = null,
                    auctionFile = null,
                    failedAttempts = 0,
                    updateLog = mutableListOf(),
                ),
            realms = emptyList(),
        )
}
