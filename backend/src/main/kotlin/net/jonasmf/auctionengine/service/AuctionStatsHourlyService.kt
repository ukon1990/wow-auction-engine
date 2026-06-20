package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.repository.rds.AuctionStatsHourlyJDBCRepository
import net.jonasmf.auctionengine.utility.JvmRuntimeDiagnostics
import net.jonasmf.auctionengine.utility.resolveZone
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

data class HourlyPriceStatisticsSummary(
    val insertedRows: Int,
    val processedAuctions: Int = 0,
)

@Service
class AuctionStatsHourlyService(
    val auctionStatsHourlyJDBCRepository: AuctionStatsHourlyJDBCRepository,
) {
    private val logger: Logger = LoggerFactory.getLogger(AuctionStatsHourlyService::class.java)

    fun updateHourlyStatsForRealm(
        connectedRealm: ConnectedRealm,
        lastModified: ZonedDateTime,
        connectedRealmUpdateHistoryId: Long,
    ): HourlyPriceStatisticsSummary {
        val startTime = System.currentTimeMillis()
        val zone = connectedRealm.resolveZone(defaultZone = lastModified.zone)
        val localLastModified = lastModified.withZoneSameInstant(zone)
        val hour = localLastModified.hour
        var processedAuctions = 0

        val insertedRows =
            auctionStatsHourlyJDBCRepository.updateHourlyStats(
                hour,
                connectedRealmUpdateHistoryId,
            )
        logger.info(
            "Processed hourly auctions for realm {} with insertedRows={} in {} ms {}",
            connectedRealm.id,
            insertedRows,
            System.currentTimeMillis() - startTime,
            JvmRuntimeDiagnostics.snapshot(),
        )
        return HourlyPriceStatisticsSummary(
            insertedRows = insertedRows,
            processedAuctions = processedAuctions,
        )
    }
}
