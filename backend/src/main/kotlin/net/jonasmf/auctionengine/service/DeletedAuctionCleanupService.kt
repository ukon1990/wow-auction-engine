package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.DeletedAuctionCleanupProperties
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.rds.DeletedAuctionCleanupRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

enum class DeletedAuctionCleanupType(
    val tableName: String,
) {
    HOURLY_STATS("auction_stats_hourly"),
    DAILY_STATS("auction_stats_daily"),
    PRICE_HISTORY("auction_price"),
}

data class DeletedAuctionCleanupRunResult(
    val type: DeletedAuctionCleanupType,
    val connectedRealmId: Int?,
    val cutoff: Instant,
    val deletedRows: Int,
)

@Service
class DeletedAuctionCleanupService(
    private val properties: DeletedAuctionCleanupProperties,
    private val cleanupRepository: DeletedAuctionCleanupRepository,
    private val auctionHouseRepository: AuctionHouseRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(DeletedAuctionCleanupService::class.java)

    fun cleanupHourlyStats(): List<DeletedAuctionCleanupRunResult> =
        cleanup(
            type = DeletedAuctionCleanupType.HOURLY_STATS,
            cutoff = clock.instant().minus(properties.hourlyRetention),
            findRealms = cleanupRepository::findNextHourlyCleanupRealms,
            deleteAction = cleanupRepository::deleteHourlyBeforeOrEqualToCutoff,
            updateMarker = auctionHouseRepository::updateLastHistoryDeleteEvent,
        )

    fun cleanupDailyStats(): List<DeletedAuctionCleanupRunResult> =
        cleanup(
            type = DeletedAuctionCleanupType.DAILY_STATS,
            cutoff = clock.instant().minus(properties.dailyRetention),
            findRealms = cleanupRepository::findNextDailyCleanupRealms,
            deleteAction = cleanupRepository::deleteDailyBeforeOrEqualToCutoff,
            updateMarker = auctionHouseRepository::updateLastHistoryDeleteEventDaily,
        )

    fun cleanupPriceHistory(): List<DeletedAuctionCleanupRunResult> =
        cleanup(
            type = DeletedAuctionCleanupType.PRICE_HISTORY,
            cutoff = clock.instant().minus(properties.priceRetention),
            findRealms = cleanupRepository::findNextPriceCleanupRealms,
            deleteAction = cleanupRepository::deletePriceForRealmBeforeOrEqualToCutoff,
            updateMarker = auctionHouseRepository::updateLastAuctionPriceDeleteEvent,
        )

    private fun cleanup(
        type: DeletedAuctionCleanupType,
        cutoff: Instant,
        findRealms: () -> List<Int?>,
        deleteAction: (Int, Instant) -> Int,
        updateMarker: (Int, Instant) -> Int,
    ): List<DeletedAuctionCleanupRunResult> {
        if (!properties.enabled) {
            logger.info("Skipping {} cleanup because deleted auction cleanup is disabled.", type)
            return listOf(emptyResult(type, cutoff))
        }
        val startTime = clock.instant().toEpochMilli()

        val connectedRealmIds = findRealms().filterNotNull()
        val deletionsPerConnectedRealm = mutableListOf<DeletedAuctionCleanupRunResult>()

        if (connectedRealmIds.isEmpty()) {
            logger.info("There were no connected realms to cleanup the history for")
            return emptyList()
        }
        logger.info("Starting to cleanup for {} connected realms that needs cleanup", connectedRealmIds.size)
        var numberOfDeletedRows = 0
        for (connectedRealmId in connectedRealmIds) {
            val result = deleteOldHistoryForConnectedRealm(type, connectedRealmId, cutoff, deleteAction, updateMarker)
            if (result != null && result.deletedRows > 0) {
                deletionsPerConnectedRealm.add(result)
                numberOfDeletedRows += result.deletedRows
            }
        }

        logger.info(
            "Cleanup completed in {} ms for {} connected realms. {} rows deleted in total",
            Instant.now().toEpochMilli() - startTime,
            deletionsPerConnectedRealm.size,
            numberOfDeletedRows,
        )
        /*
         * We only want to optimize the table, if there has been done deletions
         * This allows me to not have to keep a value stored in memory or in the database for this.
         * And Ideally, I'll get most realms at the same time, so we should be good here.
         */
        if (numberOfDeletedRows > 0) {
            logger.info("Deleted {} rows during the cleanup", numberOfDeletedRows)
            optimizeTable(type)
        }
        return deletionsPerConnectedRealm
    }

    private fun deleteOldHistoryForConnectedRealm(
        type: DeletedAuctionCleanupType,
        connectedRealmId: Int,
        cutoff: Instant,
        deleteAction: (Int, Instant) -> Int,
        updateMarker: (Int, Instant) -> Int,
    ): DeletedAuctionCleanupRunResult? =
        try {
            try {
                val deletedRows = deleteAction(connectedRealmId, cutoff)
                updateMarkerAfterSuccess(connectedRealmId, updateMarker)
                logger.info(
                    "Completed {} cleanup for connected realm {}. Deleted rows={}, cutoff={}",
                    type,
                    connectedRealmId,
                    deletedRows,
                    cutoff,
                )
                DeletedAuctionCleanupRunResult(
                    type = type,
                    connectedRealmId = connectedRealmId,
                    cutoff = cutoff,
                    deletedRows = deletedRows,
                )
            } catch (exception: RuntimeException) {
                logger.warn(
                    "Failed {} cleanup for connected realm {}. Leaving work eligible for retry.",
                    type,
                    connectedRealmId,
                    exception,
                )
                DeletedAuctionCleanupRunResult(
                    type = type,
                    connectedRealmId = connectedRealmId,
                    cutoff = cutoff,
                    deletedRows = 0,
                )
            }
        } catch (_: Exception) {
            null
        }

    /**
     * Updating the last updated time to now
     */
    private fun updateMarkerAfterSuccess(
        connectedRealmId: Int,
        updateMarker: (Int, Instant) -> Int,
    ) {
        updateMarker(connectedRealmId, Instant.now())
    }

    /**
     * Optimizes the tables or re-building them.
     * This is to make it so that we can clear up space in the database
     * storage as we keep getting close to the 200 GB limit.
     */
    private fun optimizeTable(type: DeletedAuctionCleanupType): Boolean {
        val startedAt = clock.instant()
        logger.info("Starting OPTIMIZE TABLE {} after {} cleanup queue drained.", type.tableName, type)
        cleanupRepository.optimizeTable(type.tableName)
        logger.info(
            "Completed OPTIMIZE TABLE {} in {}ms.",
            type.tableName,
            clock.millis() - startedAt.toEpochMilli(),
        )
        return true
    }

    private fun emptyResult(
        type: DeletedAuctionCleanupType,
        cutoff: Instant,
    ) = DeletedAuctionCleanupRunResult(type, null, cutoff, 0)
}
