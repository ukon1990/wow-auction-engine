package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.AuctionCleanupProperties
import net.jonasmf.auctionengine.domain.realm.AuctionHouse
import net.jonasmf.auctionengine.repository.rds.AuctionCleanupJdbcRepository
import net.jonasmf.auctionengine.repository.rds.AuctionCleanupTarget
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class AuctionCleanupResult(
    val target: AuctionCleanupTarget,
    val connectedRealmId: Int?,
    val candidateRows: Int,
    val deletedRows: Int,
    val optimized: Boolean,
    val dryRun: Boolean,
)

@Service
class AuctionCleanupService(
    private val auctionHouseService: AuctionHouseService,
    private val cleanupRepository: AuctionCleanupJdbcRepository,
    private val properties: AuctionCleanupProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(AuctionCleanupService::class.java)

    fun cleanupHourlyStats(): AuctionCleanupResult =
        cleanupStats(
            target = AuctionCleanupTarget.HOURLY_STATS,
            marker = CleanupMarker.HOURLY,
            deleteBefore = OffsetDateTime.now(clock).minus(properties.hourlyRetention).toLocalDate(),
            count = cleanupRepository::countHourlyStats,
            delete = cleanupRepository::deleteHourlyStats,
        )

    fun cleanupDailyStats(): AuctionCleanupResult =
        cleanupStats(
            target = AuctionCleanupTarget.DAILY_STATS,
            marker = CleanupMarker.DAILY,
            deleteBefore = OffsetDateTime.now(clock).minus(properties.dailyRetention).toLocalDate(),
            count = cleanupRepository::countDailyStats,
            delete = cleanupRepository::deleteDailyStats,
        )

    fun cleanupDeletedAuctions(): AuctionCleanupResult =
        cleanupOffsetDateTime(
            target = AuctionCleanupTarget.AUCTION_PRICES,
            marker = CleanupMarker.HOURLY,
            deleteBefore = OffsetDateTime.now(clock).minus(properties.deletedAuctionRetention),
            count = cleanupRepository::countAuctionPrices,
            delete = cleanupRepository::deleteAuctionPrices,
        )

    private fun cleanupStats(
        target: AuctionCleanupTarget,
        marker: CleanupMarker,
        deleteBefore: LocalDate,
        count: (Int, LocalDate) -> Int,
        delete: (Int, LocalDate, Int) -> Int,
    ): AuctionCleanupResult = cleanup(target, marker, { id -> count(id, deleteBefore) }, { id ->
        delete(id, deleteBefore, properties.batchSize)
    })

    private fun cleanupOffsetDateTime(
        target: AuctionCleanupTarget,
        marker: CleanupMarker,
        deleteBefore: OffsetDateTime,
        count: (Int, OffsetDateTime) -> Int,
        delete: (Int, OffsetDateTime, Int) -> Int,
    ): AuctionCleanupResult = cleanup(target, marker, { id -> count(id, deleteBefore) }, { id ->
        delete(id, deleteBefore, properties.batchSize)
    })

    @Transactional
    internal fun cleanup(
        target: AuctionCleanupTarget,
        marker: CleanupMarker,
        count: (Int) -> Int,
        delete: (Int) -> Int,
    ): AuctionCleanupResult {
        if (!properties.enabled) {
            return AuctionCleanupResult(target, null, 0, 0, optimized = false, dryRun = properties.dryRun)
        }

        val dueAuctionHouses = dueAuctionHouses(marker)
        val auctionHouse = dueAuctionHouses.firstOrNull()
        if (auctionHouse == null) {
            return optimizeIfReady(target)
        }

        val connectedRealmId = auctionHouse.connectedId
        val candidateRows = count(connectedRealmId)
        val deletedRows =
            if (properties.dryRun || candidateRows == 0) {
                0
            } else {
                deleteBatches(connectedRealmId, delete)
            }

        if (!properties.dryRun) {
            updateMarker(marker, connectedRealmId)
        }

        log.info(
            "Auction cleanup completed target={} connectedRealmId={} candidateRows={} deletedRows={} dryRun={}",
            target,
            connectedRealmId,
            candidateRows,
            deletedRows,
            properties.dryRun,
        )

        return AuctionCleanupResult(target, connectedRealmId, candidateRows, deletedRows, optimized = false, properties.dryRun)
    }

    private fun optimizeIfReady(target: AuctionCleanupTarget): AuctionCleanupResult {
        val shouldOptimize = properties.optimizeEnabled && !properties.dryRun
        if (shouldOptimize) {
            cleanupRepository.optimize(target)
        }
        return AuctionCleanupResult(target, null, 0, 0, optimized = shouldOptimize, dryRun = properties.dryRun)
    }

    private fun deleteBatches(
        connectedRealmId: Int,
        delete: (Int) -> Int,
    ): Int {
        var totalDeleted = 0
        do {
            val deletedRows = delete(connectedRealmId)
            totalDeleted += deletedRows
        } while (deletedRows == properties.batchSize)
        return totalDeleted
    }

    private fun dueAuctionHouses(marker: CleanupMarker): List<AuctionHouse> {
        val now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)
        return when (marker) {
            CleanupMarker.HOURLY -> auctionHouseService.getReadyForHourlyStatsCleanup(now)
            CleanupMarker.DAILY -> auctionHouseService.getReadyForDailyStatsCleanup(now)
        }
    }

    private fun updateMarker(
        marker: CleanupMarker,
        connectedRealmId: Int,
    ) {
        val now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)
        when (marker) {
            CleanupMarker.HOURLY -> auctionHouseService.updateLastHistoryDeleted(connectedRealmId, now)
            CleanupMarker.DAILY -> auctionHouseService.updateLastDailyHistoryDeleted(connectedRealmId, now)
        }
    }
}

enum class CleanupMarker {
    HOURLY,
    DAILY,
}
