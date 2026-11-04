package net.jonasmf.auctionengine.auction.repository

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionStatsHourly
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface HourlyAuctionStatsRepository : JpaRepository<AuctionStatsHourly, Long>
