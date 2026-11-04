package net.jonasmf.auctionengine.auctionhouse.repository

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionStatsDaily
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DailyAuctionStatsRepository : JpaRepository<AuctionStatsDaily, Long>
