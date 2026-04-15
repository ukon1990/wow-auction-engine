package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.service.AuctionSnapshotPersistenceService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
class DeletedAuctionCleanupSchedule(
    private val auctionSnapshotPersistenceService: AuctionSnapshotPersistenceService,
) {
    private val logger: Logger = LoggerFactory.getLogger(DeletedAuctionCleanupSchedule::class.java)

    @Scheduled(
        cron = "\${app.scheduling.deleted-auction-cleanup-cron:0 0 4 * * *}",
        zone = "\${app.scheduling.deleted-auction-cleanup-zone:GMT+1}",
    )
    fun deleteSoftDeletedAuctions() {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7)
        val deletedRows = auctionSnapshotPersistenceService.deleteSoftDeletedAuctionsOlderThan(cutoff)
        logger.info("Deleted {} soft-deleted auctions older than {}", deletedRows, cutoff)
    }
}
