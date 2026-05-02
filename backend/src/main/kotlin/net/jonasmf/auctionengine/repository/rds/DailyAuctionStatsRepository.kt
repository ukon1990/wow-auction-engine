package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionStatsDaily
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DailyAuctionStatsRepository : JpaRepository<AuctionStatsDaily, Long>
