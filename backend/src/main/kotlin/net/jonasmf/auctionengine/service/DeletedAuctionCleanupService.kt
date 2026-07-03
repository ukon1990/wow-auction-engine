package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.DeletedAuctionCleanupProperties
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.rds.DeletedAuctionCleanupRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
    val candidateCount: Long,
    val deletedRows: Int,
    val batchCount: Int,
    val optimized: Boolean,
    val dryRun: Boolean,
)

@Service
class DeletedAuctionCleanupService(
    private val properties: DeletedAuctionCleanupProperties,
    private val cleanupRepository: DeletedAuctionCleanupRepository,
    private val auctionHouseRepository: AuctionHouseRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(DeletedAuctionCleanupService::class.java)

    fun cleanupHourlyStats(): DeletedAuctionCleanupRunResult =
        cleanupStats(
            type = DeletedAuctionCleanupType.HOURLY_STATS,
            cutoff = clock.instant().minus(properties.hourlyRetention),
            findRealm = cleanupRepository::findNextHourlyCleanupRealm,
            countCandidates = cleanupRepository::countHourlyCleanupCandidates,
            deleteBatch = cleanupRepository::deleteHourlyBatch,
            updateMarker = auctionHouseRepository::updateLastHistoryDeleteEvent,
        )

    fun cleanupDailyStats(): DeletedAuctionCleanupRunResult =
        cleanupStats(
            type = DeletedAuctionCleanupType.DAILY_STATS,
            cutoff = clock.instant().minus(properties.dailyRetention),
            findRealm = cleanupRepository::findNextDailyCleanupRealm,
            countCandidates = cleanupRepository::countDailyCleanupCandidates,
            deleteBatch = cleanupRepository::deleteDailyBatch,
            updateMarker = auctionHouseRepository::updateLastHistoryDeleteEventDaily,
        )

    fun cleanupPriceHistory(): DeletedAuctionCleanupRunResult =
        cleanupInstant(
            type = DeletedAuctionCleanupType.PRICE_HISTORY,
            cutoff = clock.instant().minus(properties.priceRetention),
            findRealm = cleanupRepository::findNextPriceCleanupRealm,
            countCandidates = cleanupRepository::countPriceCleanupCandidates,
            deleteBatch = cleanupRepository::deletePriceBatch,
            updateMarker = auctionHouseRepository::updateLastHistoryDeleteEvent,
        )

    private fun cleanupStats(
        type: DeletedAuctionCleanupType,
        cutoff: Instant,
        findRealm: (LocalDate) -> Int?,
        countCandidates: (Int, LocalDate) -> Long,
        deleteBatch: (Int, LocalDate, Int) -> Int,
        updateMarker: (Int, Instant) -> Int,
    ): DeletedAuctionCleanupRunResult {
        val cutoffDate = LocalDate.ofInstant(cutoff, ZoneOffset.UTC)
        return cleanup(
            type = type,
            cutoff = cutoff,
            findRealm = { findRealm(cutoffDate) },
            countCandidates = { connectedRealmId -> countCandidates(connectedRealmId, cutoffDate) },
            deleteBatch = { connectedRealmId -> deleteBatch(connectedRealmId, cutoffDate, properties.batchSize) },
            updateMarker = updateMarker,
        )
    }

    private fun cleanupInstant(
        type: DeletedAuctionCleanupType,
        cutoff: Instant,
        findRealm: (Instant) -> Int?,
        countCandidates: (Int, Instant) -> Long,
        deleteBatch: (Int, Instant, Int) -> Int,
        updateMarker: (Int, Instant) -> Int,
    ): DeletedAuctionCleanupRunResult =
        cleanup(
            type = type,
            cutoff = cutoff,
            findRealm = { findRealm(cutoff) },
            countCandidates = { connectedRealmId -> countCandidates(connectedRealmId, cutoff) },
            deleteBatch = { connectedRealmId -> deleteBatch(connectedRealmId, cutoff, properties.batchSize) },
            updateMarker = updateMarker,
        )

    private fun cleanup(
        type: DeletedAuctionCleanupType,
        cutoff: Instant,
        findRealm: () -> Int?,
        countCandidates: (Int) -> Long,
        deleteBatch: (Int) -> Int,
        updateMarker: (Int, Instant) -> Int,
    ): DeletedAuctionCleanupRunResult {
        if (!properties.enabled) {
            logger.info("Skipping {} cleanup because deleted auction cleanup is disabled.", type)
            return emptyResult(type, cutoff)
        }

        val connectedRealmId = findRealm()
        if (connectedRealmId == null) {
            val optimized = optimizeWhenEnabled(type)
            return DeletedAuctionCleanupRunResult(type, null, cutoff, 0, 0, 0, optimized, properties.dryRun)
        }

        val candidateCount = countCandidates(connectedRealmId)
        if (properties.dryRun) {
            logger.info(
                "Dry-run {} cleanup found {} candidates for connected realm {} older than {}.",
                type,
                candidateCount,
                connectedRealmId,
                cutoff,
            )
            return DeletedAuctionCleanupRunResult(type, connectedRealmId, cutoff, candidateCount, 0, 0, false, true)
        }

        return try {
            val (deletedRows, batchCount) = deleteAllBatches { deleteBatch(connectedRealmId) }
            updateMarkerAfterSuccess(connectedRealmId, cutoff, updateMarker)
            val optimized = if (findRealm() == null) optimizeWhenEnabled(type) else false
            logger.info(
                "Completed {} cleanup for connected realm {}. candidates={}, deletedRows={}, batches={}, cutoff={}",
                type,
                connectedRealmId,
                candidateCount,
                deletedRows,
                batchCount,
                cutoff,
            )
            DeletedAuctionCleanupRunResult(
                type = type,
                connectedRealmId = connectedRealmId,
                cutoff = cutoff,
                candidateCount = candidateCount,
                deletedRows = deletedRows,
                batchCount = batchCount,
                optimized = optimized,
                dryRun = false,
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
                candidateCount = candidateCount,
                deletedRows = 0,
                batchCount = 0,
                optimized = false,
                dryRun = false,
            )
        }
    }

    private fun deleteAllBatches(deleteBatch: () -> Int): Pair<Int, Int> {
        var totalDeleted = 0
        var batches = 0
        do {
            val deleted = deleteBatch()
            if (deleted > 0) {
                totalDeleted += deleted
                batches += 1
            }
        } while (deleted == properties.batchSize)

        return totalDeleted to batches
    }

    private fun updateMarkerAfterSuccess(
        connectedRealmId: Int,
        cutoff: Instant,
        updateMarker: (Int, Instant) -> Int,
    ) {
        updateMarker(connectedRealmId, cutoff)
    }

    private fun optimizeWhenEnabled(type: DeletedAuctionCleanupType): Boolean {
        if (!properties.optimizeEnabled || properties.dryRun) {
            logger.info(
                "No {} cleanup candidates remain. optimizeEnabled={}, dryRun={}.",
                type,
                properties.optimizeEnabled,
                properties.dryRun,
            )
            return false
        }

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
    ) = DeletedAuctionCleanupRunResult(type, null, cutoff, 0, 0, 0, false, properties.dryRun)
}
