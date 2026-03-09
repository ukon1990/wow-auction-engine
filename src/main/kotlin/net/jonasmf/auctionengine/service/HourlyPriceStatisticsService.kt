package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionStatsId
import net.jonasmf.auctionengine.dbo.rds.auction.HourlyAuctionStats
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.repository.rds.HourlyPriceStatisticsRepository
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class HourlyPriceStatisticsService(
    val hourlyPriceStatisticsRepository: HourlyPriceStatisticsRepository,
) {
    fun processHourlyPriceStatistics(
        connectedRealm: ConnectedRealm,
        auctions: List<AuctionDTO>,
        lastModified: ZonedDateTime,
    ): List<HourlyAuctionStats> {
        val now = System.currentTimeMillis()
        val hourlyStatistics = mutableListOf<HourlyAuctionStats>()
        val hourlyStatisticsMap = mutableMapOf<String, HourlyAuctionStats>()

        for (auction in auctions) {
            val hourTimestamp = lastModified.hour
            val price = auction.buyout ?: auction.unit_price ?: 0
            val quantity = auction.quantity.takeIf { it > 0 } ?: 1
            val id =
                AuctionStatsId(
                    connectedRealm = connectedRealm,
                    gameBuildVersion = GameBuildVersion.RETAIL,
                    itemId = auction.item.id,
                    petSpeciesId = auction.item.pet_species_id,
                    date = lastModified.toLocalDate(),
                )
            val existingEntry = hourlyStatisticsMap[id.toString()]
            if (existingEntry != null) {
                existingEntry.apply {
                    // Dynamic path key based on hourlyAuctionStatsRepository
                    val hourKey = "price$hourTimestamp"
                    val hourQuantityKey = "quantity$hourTimestamp"
                }
            } else {
                // TODO - Create new entry
            }
        }

        return hourlyStatistics
    }
}
