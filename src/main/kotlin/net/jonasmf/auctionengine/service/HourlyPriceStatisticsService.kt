package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.repository.rds.HourlyPriceStatisticsRepository
import net.jonasmf.auctionengine.repository.rds.HourlyStatsUpsertRow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class HourlyPriceStatisticsService(
    val hourlyPriceStatisticsRepository: HourlyPriceStatisticsRepository,
) {
    private val logger: Logger = LoggerFactory.getLogger(HourlyPriceStatisticsService::class.java)

    fun processHourlyPriceStatistics(
        connectedRealm: ConnectedRealm,
        auctions: List<AuctionDTO>,
        lastModified: ZonedDateTime,
    ): List<HourlyStatsUpsertRow> {
        val startTime = System.currentTimeMillis()
        val hour = lastModified.hour
        val date = lastModified.toLocalDate()
        val grouped = linkedMapOf<String, HourlyStatsUpsertRow>()

        for (auction in auctions) {
            val itemId = auction.item.id
            val petSpeciesId = auction.item.pet_species_id
            val petBreedId = auction.item.pet_breed_id
            val petLevel = auction.item.pet_level
            val modifiers = auction.item.modifiers
            val key = "${connectedRealm.id}|${GameBuildVersion.RETAIL.ordinal}|$itemId|$date|${petSpeciesId ?: ""}"
            val price = auction.buyout ?: auction.unit_price ?: 0L
            val quantity = auction.quantity.takeIf { it > 0 } ?: 1L

            val existing = grouped[key]
            if (existing == null) {
                grouped[key] =
                    HourlyStatsUpsertRow(
                        connectedRealmId = connectedRealm.id,
                        ahTypeId = GameBuildVersion.RETAIL.ordinal,
                        itemId = itemId,
                        date = date,
                        petSpeciesId = petSpeciesId,
                        price = price,
                        quantity = quantity,
                    )
            } else {
                grouped[key] =
                    existing.copy(
                        quantity = (existing.quantity ?: 0L) + quantity,
                        price = (existing.price ?: 0).coerceAtMost(price),
                    )
            }
        }

        val insertedRows = hourlyPriceStatisticsRepository.upsertHour(grouped.values.toList(), hour)
        logger.info(
            "Processed hourly auctions for ${
                connectedRealm.auctionHouse.id
            } with $insertedRows rows, in ${System.currentTimeMillis() - startTime} ms",
        )
        return grouped.values.toList()
    }
}
