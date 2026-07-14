package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.constant.TsmSubjectType
import net.jonasmf.auctionengine.dbo.rds.tsm.TsmRegionMetric
import net.jonasmf.auctionengine.integration.tsm.TsmPublicDataClient
import net.jonasmf.auctionengine.integration.tsm.TsmRegionCsvRow
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.rds.TsmRegionMetricRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.toKotlinInstant

@Service
class TsmRegionSyncService(
    private val properties: BlizzardApiProperties,
    private val tsmPublicDataClient: TsmPublicDataClient,
    private val tsmRegionMetricRepository: TsmRegionMetricRepository,
    private val auctionHouseRepository: AuctionHouseRepository,
    private val auctionHouseService: AuctionHouseService,
    private val clock: Clock = Clock.systemUTC(),
    @Value("\${app.scheduling.tsm-region-sync-zone:GMT+1}")
    private val scheduleZone: String = "GMT+1",
) {
    private val log = LoggerFactory.getLogger(TsmRegionSyncService::class.java)

    fun syncConfiguredRegions() {
        val zone = ZoneId.of(scheduleZone)
        log.info(
            "Starting TSM region sync for configured regions {} in zone {}",
            properties.configuredRegions,
            zone,
        )
        properties.configuredRegions.forEach { region ->
            syncRegion(region, zone)
        }
        log.info(
            "Finished TSM region sync pass for configured regions {}",
            properties.configuredRegions,
        )
    }

    fun syncRegion(
        region: Region,
        zone: ZoneId = ZoneId.of(scheduleZone),
    ) {
        val connectedRealmId = CommodityRealms.idFor(region)
        val auctionHouse =
            auctionHouseRepository.findByConnectedId(connectedRealmId).orElse(null)
        if (auctionHouse == null) {
            log.warn(
                "Skipping TSM region sync for region={} connectedRealmId={} because commodity auction house is missing",
                region,
                connectedRealmId,
            )
            return
        }

        val lastTsmRegionSync = auctionHouse.lastTsmRegionSync
        if (!shouldSyncRegion(lastTsmRegionSync, zone)) {
            log.info(
                "Skipping TSM region sync for region={} connectedRealmId={} because already synced today (lastTsmRegionSync={})",
                region,
                connectedRealmId,
                lastTsmRegionSync,
            )
            return
        }

        log.info(
            "Starting TSM region sync for region={} connectedRealmId={} lastTsmRegionSync={}",
            region,
            connectedRealmId,
            lastTsmRegionSync,
        )

        try {
            val itemRows = tsmPublicDataClient.downloadItems(region)
            if (itemRows.isEmpty()) {
                log.warn(
                    "Skipping TSM region sync for region={} connectedRealmId={} because items.csv had no data rows; marker not updated",
                    region,
                    connectedRealmId,
                )
                return
            }

            val petRows = tsmPublicDataClient.downloadPets(region)
            val metrics =
                itemRows.map { it.toMetric(region, TsmSubjectType.ITEM) } +
                    petRows.map { it.toMetric(region, TsmSubjectType.PET) }

            val upserted = tsmRegionMetricRepository.upsertAll(metrics)
            val now = clock.instant().toKotlinInstant()
            val updated =
                auctionHouseService.updateLastTsmRegionSync(connectedRealmId, now)
            if (updated == 0) {
                error(
                    "TSM region sync marker update affected 0 rows for region=$region connectedRealmId=$connectedRealmId",
                )
            }

            log.info(
                "Completed TSM region sync for region={} connectedRealmId={} itemRows={} petRows={} upserted={} lastTsmRegionSync={}",
                region,
                connectedRealmId,
                itemRows.size,
                petRows.size,
                upserted,
                now,
            )
        } catch (exception: Exception) {
            log.error(
                "Failed TSM region sync for region={} connectedRealmId={} — marker not updated; prior data retained",
                region,
                connectedRealmId,
                exception,
            )
        }
    }

    fun shouldSyncRegion(
        lastTsmRegionSync: Instant?,
        zone: ZoneId,
        now: Instant = clock.instant(),
    ): Boolean {
        if (lastTsmRegionSync == null) {
            return true
        }
        val lastSyncDate = lastTsmRegionSync.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return lastSyncDate.isBefore(today)
    }
}

private fun TsmRegionCsvRow.toMetric(
    region: Region,
    subjectType: TsmSubjectType,
) = TsmRegionMetric(
    region = region,
    subjectType = subjectType,
    subjectId = subjectId,
    saleRate = saleRate,
    soldPerDay = soldPerDay,
    marketValue = marketValue,
    historical = historical,
    avgSalePrice = avgSalePrice,
    sourceUpdatedAt = sourceUpdatedAt,
)
