package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.ZonedDateTime

data class UpsertAuctionParams(
    val id: Long,
    val connectedRealmId: Int,
    val itemId: Long,
    val quantity: Long,
    val unitPrice: Long?,
    val timeLeft: Int,
    val buyout: Long?,
    val firstSeen: ZonedDateTime?,
    val lastSeen: ZonedDateTime?,
    val updateHistoryId: Long,
)

@Repository
class AuctionJDBCRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional
    fun upsertAuctions(auctions: List<UpsertAuctionParams>): Int {
        if (auctions.isEmpty()) return 0

        // chunk to avoid creating excessively large SQL statements/parameter lists
        val chunkSize = 5_000
        var totalRows = 0

        auctions.chunked(chunkSize).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" }

            val sqlPrefix = (
                "INSERT INTO auction (id, connected_realm_id, item_id, quantity, " +
                    "unit_price, time_left, buyout, first_seen, last_seen, update_history_id) VALUES "
            )
            val updateClause = (
                " ON DUPLICATE KEY UPDATE " +
                    "quantity = VALUES(quantity), " +
                    "unit_price = VALUES(unit_price), " +
                    "time_left = VALUES(time_left), " +
                    "buyout = VALUES(buyout), " +
                    "last_seen = VALUES(last_seen), " +
                    "update_history_id = VALUES(update_history_id)"
            )

            val sql =
                StringBuilder()
                    .append(sqlPrefix)
                    .append(placeholders)
                    .append(updateClause)
                    .toString()

            val params = ArrayList<Any?>(chunk.size * 10)
            for (auction in chunk) {
                params.add(auction.id)
                params.add(auction.connectedRealmId)
                params.add(auction.itemId)
                params.add(auction.quantity)
                params.add(auction.unitPrice)
                params.add(auction.timeLeft)
                params.add(auction.buyout)
                params.add(auction.firstSeen?.let { Timestamp.from(it.toInstant()) })
                params.add(auction.lastSeen?.let { Timestamp.from(it.toInstant()) })
                params.add(auction.updateHistoryId)
            }

            totalRows += jdbcTemplate.update(sql, *params.toTypedArray())
        }

        return totalRows
    }
}
