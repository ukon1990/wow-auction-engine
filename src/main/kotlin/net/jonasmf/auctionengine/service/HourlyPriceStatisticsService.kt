package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.ModifierDTO
import net.jonasmf.auctionengine.repository.rds.HourlyPriceStatisticsRepository
import net.jonasmf.auctionengine.repository.rds.HourlyStatsUpsertRow
import net.jonasmf.auctionengine.utility.JvmRuntimeDiagnostics
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

data class HourlyPriceStatisticsSummary(
    val insertedRows: Int,
    val groupedRows: Int,
)

@Service
class HourlyPriceStatisticsService(
    val hourlyPriceStatisticsRepository: HourlyPriceStatisticsRepository,
) {
    private val logger: Logger = LoggerFactory.getLogger(HourlyPriceStatisticsService::class.java)
    private val progressLogInterval = 100_000

    fun processHourlyPriceStatistics(
        connectedRealm: ConnectedRealm,
        auctions: List<AuctionDTO>,
        lastModified: ZonedDateTime,
    ): HourlyPriceStatisticsSummary {
        val startTime = System.currentTimeMillis()
        val hour = lastModified.hour
        val date = lastModified.toLocalDate()
        val grouped = linkedMapOf<String, HourlyStatsUpsertRow>()

        logger.info(
            "Starting hourly stats aggregation for realm {} with {} auctions at hour {} and {}",
            connectedRealm.id,
            auctions.size,
            hour,
            JvmRuntimeDiagnostics.snapshot(),
        )

        for ((index, auction) in auctions.withIndex()) {
            val itemId = auction.item.id
            val petSpeciesId = auction.item.pet_species_id
            val modifierKey = canonicalModifierKey(auction.item.modifiers)
            val key = "${connectedRealm.id}|${
                GameBuildVersion.RETAIL.ordinal
            }|$itemId|$date|${
                petSpeciesId ?: ""
            }|$modifierKey"
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
                        modifierKey = modifierKey,
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

            if ((index + 1) % progressLogInterval == 0) {
                logger.info(
                    "Hourly stats aggregation progress for realm {}: {}/{} auctions groupedRows={} {}",
                    connectedRealm.id,
                    index + 1,
                    auctions.size,
                    grouped.size,
                    JvmRuntimeDiagnostics.snapshot(),
                )
            }
        }

        val groupedRows = ArrayList(grouped.values)
        logger.info(
            "Starting hourly stats upsert for realm {} with groupedRows={} at hour {} {}",
            connectedRealm.id,
            groupedRows.size,
            hour,
            JvmRuntimeDiagnostics.snapshot(),
        )
        val insertedRows = hourlyPriceStatisticsRepository.upsertHour(groupedRows, hour)
        grouped.clear()
        logger.info(
            "Processed hourly auctions for realm {} with insertedRows={} groupedRows={} in {} ms {}",
            connectedRealm.id,
            insertedRows,
            groupedRows.size,
            System.currentTimeMillis() - startTime,
            JvmRuntimeDiagnostics.snapshot(),
        )
        return HourlyPriceStatisticsSummary(
            insertedRows = insertedRows,
            groupedRows = groupedRows.size,
        )
    }

    private fun canonicalModifierKey(modifiers: List<ModifierDTO>?): String =
        modifiers
            .orEmpty()
            .sortedBy { it.value }
            .joinToString(",") { it.value.toString() }
}
