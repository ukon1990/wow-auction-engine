package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.repository.rds.HourlyPriceStatisticsRepository
import org.springframework.stereotype.Service

@Service
class HourlyPriceStatisticsService(
    val hourlyPriceStatisticsRepository: HourlyPriceStatisticsRepository,
) {
    fun processHourlyPriceStatistics() {}
}
