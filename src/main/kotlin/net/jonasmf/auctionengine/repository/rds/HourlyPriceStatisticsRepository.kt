package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.auction.HourlyAuctionStats
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface HourlyPriceStatisticsRepository : JpaRepository<HourlyAuctionStats, Long>
