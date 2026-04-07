package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.service.AuctionHouseService
import net.jonasmf.auctionengine.service.BlizzardAuctionService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class AuctionHouseSchedule(
    private val properties: BlizzardApiProperties,
    private val blizzardAuctionService: BlizzardAuctionService,
    private val auctionHouseService: AuctionHouseService,
) {
    val logger: Logger = LoggerFactory.getLogger(AuctionHouseSchedule::class.java)
    private val updateBatchRunning = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "PT1M", initialDelay = 3_000)
    fun checkForUpdates() {
        if (!updateBatchRunning.compareAndSet(false, true)) {
            logger.info("Skipping scheduled auction house update check because an update batch is already running.")
            return
        }

        logger.info("Starting scheduled auction house update check...")
        try {
            val auctionHousesToUpdate = auctionHouseService.getReadyForUpdate(properties.region)
            if (auctionHousesToUpdate.isEmpty()) {
                logger.info("No auction houses found for update.")
                return
            }
            logger.info("Found ${auctionHousesToUpdate.size} auction houses ready for update.")
            blizzardAuctionService.updateAuctionHouses(auctionHousesToUpdate)
            logger.info("Completed scheduled auction house update batch.")
        } finally {
            updateBatchRunning.set(false)
        }
    }
}
