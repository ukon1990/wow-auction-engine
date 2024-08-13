package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity

@Entity
data class HourlyAuctionStats(
    @EmbeddedId
    val id: AuctionStatsId,

    // Hourly price and quantity data
    val price00: Long?, val quantity00: Long?,
    val price01: Long?, val quantity01: Long?,
    val price02: Long?, val quantity02: Long?,
    val price03: Long?, val quantity03: Long?,
    val price04: Long?, val quantity04: Long?,
    val price05: Long?, val quantity05: Long?,
    val price06: Long?, val quantity06: Long?,
    val price07: Long?, val quantity07: Long?,
    val price08: Long?, val quantity08: Long?,
    val price09: Long?, val quantity09: Long?,
    val price10: Long?, val quantity10: Long?,
    val price11: Long?, val quantity11: Long?,
    val price12: Long?, val quantity12: Long?,
    val price13: Long?, val quantity13: Long?,
    val price14: Long?, val quantity14: Long?,
    val price15: Long?, val quantity15: Long?,
    val price16: Long?, val quantity16: Long?,
    val price17: Long?, val quantity17: Long?,
    val price18: Long?, val quantity18: Long?,
    val price19: Long?, val quantity19: Long?,
    val price20: Long?, val quantity20: Long?,
    val price21: Long?, val quantity21: Long?,
    val price22: Long?, val quantity22: Long?,
    val price23: Long?, val quantity23: Long?
)
