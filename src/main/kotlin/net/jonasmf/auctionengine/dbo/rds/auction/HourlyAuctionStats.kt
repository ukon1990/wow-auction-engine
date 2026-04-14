package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * The reason I have gone with this approach instead of having multiple rows per day, is 1 to save space,
 * allow for fewer inserts and reads to the database and for speed.
 *
 * Since we have so many rows, we constantly need to index if we were to have a structure with,
 * one row per connected realm per item per hour. I tried it before, and the indexes took up a lot of space, and also
 * there were slowdown due to the indexation when I upserted hundreds of thousands of rows into the database at the same time.
 *
 * This was the fastest and cheapest approach I could find as I don't want to spend lots of money on an expensive database.
 */
@Entity
@Table(
    name = "hourly_auction_stats",
    indexes = [
        Index(
            name = "idx_hourly_auction_stats_connected_realm_id_date",
            columnList = "connected_realm_id, date",
        ),
        Index(
            name = "idx_hourly_auction_stats_connected_realm_id_item_id_date",
            columnList = "connected_realm_id, item_id, date",
        ),
    ],
)
data class HourlyAuctionStats(
    @EmbeddedId
    val id: AuctionStatsId,
    // Hourly price and quantity data
    var price00: Long?,
    var quantity00: Long?,
    var price01: Long?,
    var quantity01: Long?,
    var price02: Long?,
    var quantity02: Long?,
    var price03: Long?,
    var quantity03: Long?,
    var price04: Long?,
    var quantity04: Long?,
    var price05: Long?,
    var quantity05: Long?,
    var price06: Long?,
    var quantity06: Long?,
    var price07: Long?,
    var quantity07: Long?,
    var price08: Long?,
    var quantity08: Long?,
    var price09: Long?,
    var quantity09: Long?,
    var price10: Long?,
    var quantity10: Long?,
    var price11: Long?,
    var quantity11: Long?,
    var price12: Long?,
    var quantity12: Long?,
    var price13: Long?,
    var quantity13: Long?,
    var price14: Long?,
    var quantity14: Long?,
    var price15: Long?,
    var quantity15: Long?,
    var price16: Long?,
    var quantity16: Long?,
    var price17: Long?,
    var quantity17: Long?,
    var price18: Long?,
    var quantity18: Long?,
    var price19: Long?,
    var quantity19: Long?,
    var price20: Long?,
    var quantity20: Long?,
    var price21: Long?,
    var quantity21: Long?,
    var price22: Long?,
    var quantity22: Long?,
    var price23: Long?,
    var quantity23: Long?,
)
