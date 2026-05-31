package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.auction.Auction
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionPrice
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.OffsetDateTime

private const val AUCTION_JDBC_CHUNK_SIZE = 1_000

@Repository
class AuctionJDBCRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional
    fun upsertAuctions(auctions: Collection<Auction>): Int {
        if (auctions.isEmpty()) return 0
        var totalRows = 0
        val columnCount = 14
        auctions
            .sortedWith(compareBy(Auction::id, Auction::id))
            .chunked(AUCTION_JDBC_CHUNK_SIZE)
            .forEach { chunk ->
                val sql =
                    """
                    INSERT INTO auction (
                        id,
                        connected_realm_id,
                        item_id,
                        petSpeciesId,
                        petQualityId,
                        petLevel,
                        buyout,
                        bid,
                        p25,
                        p75,
                        quantity,
                        first_seen,
                        last_seen,
                        update_history_id
                    ) VALUES (${placeholders(columnCount)})
                    ON DUPLICATE KEY UPDATE
                        buyout = VALUES(buyout),
                        bid = VALUES(bid),
                        p25 = VALUES(p25),
                        p75 = VALUES(p75),
                        quantity = VALUES(quantity),
                        last_seen = VALUES(last_seen),
                        update_history_id = VALUES(update_history_id)
                    """.trimIndent()
                val params =
                    ArrayList<Any?>(chunk.size * columnCount).apply {
                        chunk.forEach { auction ->
                            add(auction.id)
                            add(auction.connectedRealm.id)
                            add(auction.itemId)
                            add(auction.petSpeciesId)
                            add(auction.petQualityId)
                            add(auction.petLevel)
                            add(auction.modifierKey)
                            add(auction.bonusKey)
                            add(auction.buyout)
                            add(auction.bid)
                            add(auction.p25)
                            add(auction.p75)
                            add(auction.quantity)
                            add(auction.firstSeen?.toSqlTimestamp())
                            add(auction.lastSeen?.toSqlTimestamp())
                            add(auction.updateHistory.id)
                        }
                    }
                totalRows += jdbcTemplate.update(sql, *params.toTypedArray())
            }
        return totalRows
    }

    @Transactional
    fun upsertAuctionPrices(auctions: Collection<AuctionPrice>): Int {
        if (auctions.isEmpty()) return 0
        var totalRows = 0
        val columnCount = 5
        auctions
            .sortedWith(compareBy(AuctionPrice::id, AuctionPrice::id))
            .chunked(AUCTION_JDBC_CHUNK_SIZE)
            .forEach { chunk ->
                val sql = // Fetch auction id via query
                    """
                    INSERT INTO auction_price (
                        id,
                        auction_id,
                        buyout,
                        bid,
                        quantity,
                        last_modified
                    ) VALUES (${placeholders(columnCount)})
                    ON DUPLICATE KEY UPDATE
                        buyout = VALUES(buyout),
                        bid = VALUES(bid),
                        quantity = VALUES(quantity),
                        last_seen = VALUES(last_seen),
                        update_history_id = VALUES(update_history_id)
                    """.trimIndent()
                val params =
                    ArrayList<Any?>(chunk.size * columnCount).apply {
                        chunk.forEach { auction ->
                            add(auction.id)
                            add(auction.auction.id)
                            add(auction.buyout)
                            add(auction.bid)
                            add(auction.quantity)
                        }
                    }
                totalRows += jdbcTemplate.update(sql, *params.toTypedArray())
            }
        return totalRows
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")
}

private fun OffsetDateTime.toSqlTimestamp(): Timestamp = Timestamp.from(toInstant())
