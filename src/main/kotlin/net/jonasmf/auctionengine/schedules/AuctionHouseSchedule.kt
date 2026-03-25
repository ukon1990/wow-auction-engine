package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.service.AuctionHouseService
import net.jonasmf.auctionengine.service.BlizzardAuctionService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class AuctionHouseSchedule(
    private val properties: BlizzardApiProperties,
    private val blizzardAuctionService: BlizzardAuctionService,
    private val auctionHouseService: AuctionHouseService,
) {
    val logger: Logger = LoggerFactory.getLogger(AuctionHouseSchedule::class.java)

    @Scheduled(fixedDelayString = "PT1H", initialDelay = 3_000)
    fun checkForUpdates() {
        logger.info("Starting scheduled auction house update check...")
        val auctionHousesToUpdate = auctionHouseService.getReadyForUpdate(properties.region)
        blizzardAuctionService.updateAuctionHouses(auctionHousesToUpdate)
    }
}
