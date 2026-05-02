package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.dbo.dynamodb.converters.toKotlin
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.service.AuctionHouseService
import net.jonasmf.auctionengine.service.AuctionStatsDailyService
import net.jonasmf.auctionengine.utility.resolveZone
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.toJavaInstant

@Component
class AuctionStatsDailySchedule(
    private val properties: BlizzardApiProperties,
    private val auctionHouseService: AuctionHouseService,
    private val auctionStatsDailyService: AuctionStatsDailyService,
) {
    private val log = LoggerFactory.getLogger(AuctionStatsDailySchedule::class.java)
    private val updateRunning = AtomicBoolean(false)
    private val firstUpdateDate = LocalDate.of(2026, 1, 1)

    @Scheduled(
        fixedDelayString = "\${app.scheduling.daily-price-stats-delay:PT5M}",
        initialDelayString = "\${app.scheduling.initial-delay:PT30S}",
    )
    fun updateDailyPriceStatistics() {
        if (!updateRunning.compareAndSet(false, true)) {
            log.info("Skipping daily price statistics update because an update is already running.")
            return
        }

        try {
            log.info("Starting daily price statistics update for configured regions {}.", properties.configuredRegions)
            properties.configuredRegions.forEach { region ->
                val auctionHouses = auctionHouseService.findAllByRegion(region)
                if (auctionHouses.isEmpty()) {
                    log.info("No auction houses found for daily price statistics update in region {}.", region)
                    return@forEach
                }

                log.info(
                    "Found {} auction houses for daily price statistics update in region {}.",
                    auctionHouses.size,
                    region,
                )
                auctionHouses.forEach(::updateAuctionHouse)
            }
            log.info("Completed daily price statistics update for configured regions {}.", properties.configuredRegions)
        } finally {
            updateRunning.set(false)
        }
    }

    private fun updateAuctionHouse(auctionHouse: AuctionHouse) {
        val connectedRealmId = auctionHouse.connectedId.takeIf { it != 0 } ?: auctionHouse.id
        if (connectedRealmId == null || connectedRealmId == 0) {
            log.warn("Skipping daily price statistics update for auction house without connected realm id.")
            return
        }

        val zone = auctionHouse.resolveZone()
        val lastUpdatedDate =
            auctionHouse.lastDailyPriceUpdate
                ?.toJavaInstant()
                ?.atZone(zone)
                ?.toLocalDate()
                ?: firstUpdateDate.minusDays(1)
        val endDate = LocalDate.now(zone).minusDays(1)

        try {
            val result =
                auctionStatsDailyService.updateForDate(
                    connectedRealmId = connectedRealmId,
                    lastUpdated = lastUpdatedDate,
                    endDate = endDate,
                )
            val lastProcessedDate = result.lastProcessedDate
            if (lastProcessedDate == null) {
                log.info(
                    "No daily price statistics update needed for connected realm {}, last updated date {}, end date {}, zone {}.",
                    connectedRealmId,
                    lastUpdatedDate,
                    endDate,
                    zone,
                )
                return
            }

            val marker = lastProcessedDate.atStartOfDay(zone).toInstant().toKotlin()
            auctionHouseService.updateLastDailyPriceUpdate(connectedRealmId, marker)
            log.info(
                "Updated daily price statistics marker for connected realm {} to local date {} in zone {} after {} rows.",
                connectedRealmId,
                lastProcessedDate,
                zone,
                result.updatedRows,
            )
        } catch (exception: Exception) {
            log.error(
                "Failed daily price statistics update for connected realm {} from date {} to {} in zone {}.",
                connectedRealmId,
                lastUpdatedDate,
                endDate,
                zone,
                exception,
            )
        }
    }
}
