package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

data class ConnectedRealmUpsertRow(
    val id: Int,
    val auctionHouseId: Int,
)

data class RealmSyncRow(
    val connectedRealmId: Int,
    val realmId: Int,
    val regionId: Int,
    val name: String,
    val category: String,
    val locale: Int,
    val timezone: String,
    val gameBuild: Int,
    val slug: String,
)

@Repository
class ConnectedRealmJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val chunkSize = 5_000

    @Volatile
    private var realmJoinMetadata: RealmJoinMetadata? = null

    fun findAuctionHouseIdsByConnectedRealmIds(ids: List<Int>): Map<Int, Int> {
        if (ids.isEmpty()) return emptyMap()

        val result = mutableMapOf<Int, Int>()
        ids.distinct().chunked(chunkSize).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val sql =
                """
                SELECT id, auction_house_id
                FROM connected_realm
                WHERE id IN ($placeholders)
                """.trimIndent()

            jdbcTemplate.query(sql, { rs ->
                result[rs.getInt("id")] = rs.getInt("auction_house_id")
            }, *chunk.toTypedArray())
        }

        return result
    }

    @Transactional
    fun upsertConnectedRealms(rows: List<ConnectedRealmUpsertRow>): Int {
        if (rows.isEmpty()) return 0

        var total = 0
        rows.chunked(chunkSize).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "(?, ?)" }
            val sql =
                """
                INSERT INTO connected_realm (id, auction_house_id)
                VALUES $placeholders
                ON DUPLICATE KEY UPDATE
                    id = VALUES(id)
                """.trimIndent()

            val params = ArrayList<Any?>(chunk.size * 2)
            chunk.forEach { row ->
                params.add(row.id)
                params.add(row.auctionHouseId)
            }

            total += jdbcTemplate.update(sql, *params.toTypedArray())
        }

        return total
    }

    @Transactional
    fun replaceRealmsForConnectedRealms(
        connectedRealmIds: List<Int>,
        realmRows: List<RealmSyncRow>,
    ) {
        if (connectedRealmIds.isEmpty()) return

        val distinctConnectedRealmIds = connectedRealmIds.distinct()
        val existingRealmIds = findRealmIdsByConnectedRealmIds(distinctConnectedRealmIds)

        deleteConnectedRealmRealmRows(distinctConnectedRealmIds)
        deleteRealmRows(existingRealmIds)

        if (realmRows.isEmpty()) return

        insertRealmRows(realmRows)
        insertConnectedRealmRealmRows(realmRows)
    }

    private fun findRealmIdsByConnectedRealmIds(connectedRealmIds: List<Int>): List<Int> {
        if (connectedRealmIds.isEmpty()) return emptyList()

        val metadata = getRealmJoinMetadata()
        val realmIds = mutableListOf<Int>()
        connectedRealmIds.chunked(chunkSize).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val sql =
                """
                SELECT ${metadata.realmIdColumn}
                FROM ${metadata.tableName}
                WHERE ${metadata.connectedRealmIdColumn} IN ($placeholders)
                """.trimIndent()

            jdbcTemplate.query(sql, { rs ->
                realmIds.add(rs.getInt(metadata.realmIdColumn))
            }, *chunk.toTypedArray())
        }

        return realmIds.distinct()
    }

    private fun deleteConnectedRealmRealmRows(connectedRealmIds: List<Int>) {
        val metadata = getRealmJoinMetadata()
        connectedRealmIds.chunked(chunkSize).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val sql = "DELETE FROM ${metadata.tableName} WHERE ${metadata.connectedRealmIdColumn} IN ($placeholders)"
            jdbcTemplate.update(sql, *chunk.toTypedArray())
        }
    }

    private fun deleteRealmRows(realmIds: List<Int>) {
        if (realmIds.isEmpty()) return

        realmIds.chunked(chunkSize).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val sql = "DELETE FROM realm WHERE id IN ($placeholders)"
            jdbcTemplate.update(sql, *chunk.toTypedArray())
        }
    }

    private fun insertRealmRows(realmRows: List<RealmSyncRow>) {
        realmRows.chunked(chunkSize).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "(?, ?, ?, ?, ?, ?, ?, ?)" }
            val sql =
                """
                INSERT INTO realm (
                    id,
                    region_id,
                    name,
                    category,
                    locale,
                    timezone,
                    game_build,
                    slug
                ) VALUES $placeholders
                """.trimIndent()

            val params = ArrayList<Any?>(chunk.size * 8)
            chunk.forEach { row ->
                params.add(row.realmId)
                params.add(row.regionId)
                params.add(row.name)
                params.add(row.category)
                params.add(row.locale)
                params.add(row.timezone)
                params.add(row.gameBuild)
                params.add(row.slug)
            }

            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun insertConnectedRealmRealmRows(realmRows: List<RealmSyncRow>) {
        val metadata = getRealmJoinMetadata()
        realmRows.chunked(chunkSize).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "(?, ?)" }
            val sql =
                """
                INSERT INTO ${metadata.tableName} (
                    ${metadata.connectedRealmIdColumn},
                    ${metadata.realmIdColumn}
                ) VALUES $placeholders
                """.trimIndent()

            val params = ArrayList<Any?>(chunk.size * 2)
            chunk.forEach { row ->
                params.add(row.connectedRealmId)
                params.add(row.realmId)
            }

            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun getRealmJoinMetadata(): RealmJoinMetadata {
        realmJoinMetadata?.let { return it }

        synchronized(this) {
            realmJoinMetadata?.let { return it }

            val tableName =
                jdbcTemplate.queryForObject(
                    """
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name LIKE 'connected\_realm%realm%'
                      AND table_name <> 'connected_realm'
                    ORDER BY table_name
                    LIMIT 1
                    """.trimIndent(),
                    String::class.java,
                ) ?: error("Unable to resolve ConnectedRealm join table for realm synchronization")

            val columns =
                jdbcTemplate.queryForList(
                    """
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                    ORDER BY ordinal_position
                    """.trimIndent(),
                    String::class.java,
                    tableName,
                )

            val connectedRealmIdColumn =
                columns.firstOrNull { it.contains("connected_realm") }
                    ?: error("Unable to resolve connected realm column for join table $tableName")
            val realmIdColumn =
                columns.firstOrNull { it != connectedRealmIdColumn }
                    ?: error("Unable to resolve realm column for join table $tableName")

            return RealmJoinMetadata(
                tableName = tableName,
                connectedRealmIdColumn = connectedRealmIdColumn,
                realmIdColumn = realmIdColumn,
            ).also { realmJoinMetadata = it }
        }
    }
}

private data class RealmJoinMetadata(
    val tableName: String,
    val connectedRealmIdColumn: String,
    val realmIdColumn: String,
)
