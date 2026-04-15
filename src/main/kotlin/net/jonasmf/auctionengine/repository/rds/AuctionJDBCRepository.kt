package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.OffsetDateTime

private const val AUCTION_JDBC_CHUNK_SIZE = 1_000

data class AuctionModifierUpsertRow(
    val type: String,
    val value: Int,
)

data class AuctionItemUpsertRow(
    val variantHash: String,
    val itemId: Int,
    val bonusLists: String,
    val context: Int?,
    val petBreedId: Int?,
    val petLevel: Int?,
    val petQualityId: Int?,
    val petSpeciesId: Int?,
)

data class AuctionItemModifierLinkUpsertRow(
    val auctionItemId: Long,
    val sortOrder: Int,
    val modifierId: Long,
)

data class AuctionUpsertRow(
    val id: Long,
    val connectedRealmId: Int,
    val itemId: Long,
    val quantity: Long,
    val bid: Long?,
    val unitPrice: Long?,
    val timeLeft: Int,
    val buyout: Long?,
    val firstSeen: OffsetDateTime,
    val lastSeen: OffsetDateTime,
    val updateHistoryId: Long,
)

@Repository
class AuctionJDBCRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional
    fun upsertModifiers(modifiers: Collection<AuctionModifierUpsertRow>): Int {
        if (modifiers.isEmpty()) return 0
        var totalRows = 0
        modifiers.distinct().chunked(AUCTION_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO auction_item_modifier (type, value)
                VALUES ${chunk.joinToString(",") { "(?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    type = VALUES(type)
                """.trimIndent()
            val params = chunk.flatMap<AuctionModifierUpsertRow, Any?> { listOf(it.type, it.value) }
            totalRows += jdbcTemplate.update(sql, *params.toTypedArray())
        }
        return totalRows
    }

    fun findModifierIds(modifiers: Collection<AuctionModifierUpsertRow>): Map<AuctionModifierUpsertRow, Long> {
        if (modifiers.isEmpty()) return emptyMap()
        val rows = linkedMapOf<AuctionModifierUpsertRow, Long>()
        modifiers.distinct().chunked(AUCTION_JDBC_CHUNK_SIZE).forEach { chunk ->
            val whereClause = chunk.joinToString(" OR ") { "(type = ? AND value = ?)" }
            val params = chunk.flatMap<AuctionModifierUpsertRow, Any?> { listOf(it.type, it.value) }
            jdbcTemplate.query(
                "SELECT id, type, value FROM auction_item_modifier WHERE $whereClause",
                { rs ->
                    rows[
                        AuctionModifierUpsertRow(
                            type = rs.getString("type"),
                            value = rs.getInt("value"),
                        ),
                    ] = rs.getLong("id")
                },
                *params.toTypedArray(),
            )
        }
        return rows
    }

    @Transactional
    fun upsertAuctionItems(items: Collection<AuctionItemUpsertRow>): Int {
        if (items.isEmpty()) return 0
        var totalRows = 0
        items.distinctBy(AuctionItemUpsertRow::variantHash).chunked(AUCTION_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO auction_item (
                    variant_hash,
                    item_id,
                    bonus_lists,
                    context,
                    pet_breed_id,
                    pet_level,
                    pet_quality_id,
                    pet_species_id
                ) VALUES ${chunk.joinToString(",") { "(?, ?, ?, ?, ?, ?, ?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    item_id = VALUES(item_id),
                    bonus_lists = VALUES(bonus_lists),
                    context = VALUES(context),
                    pet_breed_id = VALUES(pet_breed_id),
                    pet_level = VALUES(pet_level),
                    pet_quality_id = VALUES(pet_quality_id),
                    pet_species_id = VALUES(pet_species_id)
                """.trimIndent()
            val params =
                chunk.flatMap<AuctionItemUpsertRow, Any?> {
                    listOf(
                        it.variantHash,
                        it.itemId,
                        it.bonusLists,
                        it.context,
                        it.petBreedId,
                        it.petLevel,
                        it.petQualityId,
                        it.petSpeciesId,
                    )
                }
            totalRows += jdbcTemplate.update(sql, *params.toTypedArray())
        }
        return totalRows
    }

    fun findAuctionItemIds(variantHashes: Collection<String>): Map<String, Long> {
        if (variantHashes.isEmpty()) return emptyMap()
        val rows = linkedMapOf<String, Long>()
        variantHashes.distinct().chunked(AUCTION_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                "SELECT id, variant_hash FROM auction_item WHERE variant_hash IN (${placeholders(chunk.size)})"
            jdbcTemplate.query(
                sql,
                { rs ->
                    rows[rs.getString("variant_hash")] = rs.getLong("id")
                },
                *chunk.toTypedArray(),
            )
        }
        return rows
    }

    @Transactional
    fun upsertAuctionItemModifierLinks(links: Collection<AuctionItemModifierLinkUpsertRow>): Int {
        if (links.isEmpty()) return 0
        var totalRows = 0
        links
            .distinctBy { Triple(it.auctionItemId, it.sortOrder, it.modifierId) }
            .chunked(AUCTION_JDBC_CHUNK_SIZE)
            .forEach { chunk ->
                val sql =
                    """
                    INSERT INTO auction_item_modifier_link (
                        auction_item_id,
                        sort_order,
                        modifier_id
                    ) VALUES ${chunk.joinToString(",") { "(?, ?, ?)" }}
                    ON DUPLICATE KEY UPDATE
                        modifier_id = VALUES(modifier_id)
                    """.trimIndent()
                val params =
                    chunk.flatMap<AuctionItemModifierLinkUpsertRow, Any?> {
                        listOf(it.auctionItemId, it.sortOrder, it.modifierId)
                    }
                totalRows += jdbcTemplate.update(sql, *params.toTypedArray())
            }
        return totalRows
    }

    @Transactional
    fun upsertAuctions(auctions: Collection<AuctionUpsertRow>): Int {
        if (auctions.isEmpty()) return 0
        var totalRows = 0
        auctions.chunked(AUCTION_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO auction (
                    id,
                    connected_realm_id,
                    item_id,
                    quantity,
                    bid,
                    unit_price,
                    time_left,
                    buyout,
                    first_seen,
                    last_seen,
                    deleted_at,
                    update_history_id
                ) VALUES ${chunk.joinToString(",") { "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    item_id = VALUES(item_id),
                    quantity = VALUES(quantity),
                    bid = VALUES(bid),
                    unit_price = VALUES(unit_price),
                    time_left = VALUES(time_left),
                    buyout = VALUES(buyout),
                    first_seen = COALESCE(first_seen, VALUES(first_seen)),
                    last_seen = VALUES(last_seen),
                    deleted_at = NULL,
                    update_history_id = VALUES(update_history_id)
                """.trimIndent()
            val params =
                ArrayList<Any?>(chunk.size * 12).apply {
                    chunk.forEach { auction ->
                        add(auction.id)
                        add(auction.connectedRealmId)
                        add(auction.itemId)
                        add(auction.quantity)
                        add(auction.bid)
                        add(auction.unitPrice)
                        add(auction.timeLeft)
                        add(auction.buyout)
                        add(auction.firstSeen.toSqlTimestamp())
                        add(auction.lastSeen.toSqlTimestamp())
                        add(null)
                        add(auction.updateHistoryId)
                    }
                }
            totalRows += jdbcTemplate.update(sql, *params.toTypedArray())
        }
        return totalRows
    }

    @Transactional
    fun markMissingAuctionsDeleted(
        connectedRealmId: Int,
        updateHistoryId: Long,
        deletedAt: OffsetDateTime,
    ): Int =
        jdbcTemplate.update(
            """
            UPDATE auction
            SET deleted_at = ?
            WHERE connected_realm_id = ?
              AND update_history_id <> ?
              AND deleted_at IS NULL
            """.trimIndent(),
            deletedAt.toSqlTimestamp(),
            connectedRealmId,
            updateHistoryId,
        )

    @Transactional
    fun deleteSoftDeletedAuctionsOlderThan(cutoff: OffsetDateTime): Int =
        jdbcTemplate.update(
            """
            DELETE FROM auction
            WHERE deleted_at IS NOT NULL
              AND deleted_at < ?
            """.trimIndent(),
            cutoff.toSqlTimestamp(),
        )

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")
}

private fun OffsetDateTime.toSqlTimestamp(): Timestamp = Timestamp.from(toInstant())
