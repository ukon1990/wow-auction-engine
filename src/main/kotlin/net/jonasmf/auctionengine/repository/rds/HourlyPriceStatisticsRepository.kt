package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

data class HourlyStatsUpsertRow(
    val connectedRealmId: Int,
    val ahTypeId: Int,
    val itemId: Int,
    val date: LocalDate,
    val petSpeciesId: Int?,
    val price: Long?,
    val quantity: Long?,
)

@Repository
class HourlyPriceStatisticsRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val CHUNK_SIZE = 5_000

    @Transactional
    fun upsertHour(
        rows: List<HourlyStatsUpsertRow>,
        hour: Int,
    ): Int {
        if (rows.isEmpty()) return 0
        require(hour in 0..23) { "Hour must be between 0 and 23" }

        val priceColumn = "price%02d".format(hour)
        val quantityColumn = "quantity%02d".format(hour)
        val tableName = "hourly_auction_stats"
        val numberOfColumns = 7
        var total = 0
        val valueTuple = List(numberOfColumns) { "?" }.joinToString(",", "(", ")")

        rows.chunked(CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { valueTuple }
            val sql =
                """
                INSERT INTO $tableName (
                    connected_realm_id,
                    ah_type_id,
                    item_id,
                    date,
                    pet_species_id,
                    $priceColumn,
                    $quantityColumn
                ) VALUES $placeholders
                ON DUPLICATE KEY UPDATE
                    $priceColumn = VALUES($priceColumn),
                    $quantityColumn = VALUES($quantityColumn)
                """.trimIndent()
            // What is the 7 here? Bytes?
            val params = ArrayList<Any?>(chunk.size * numberOfColumns)
            for (row in chunk) {
                params.add(row.connectedRealmId)
                params.add(row.ahTypeId)
                params.add(row.itemId)
                params.add(Date.valueOf(row.date))
                params.add(row.petSpeciesId ?: -1)
                params.add(row.price)
                params.add(row.quantity)
            }
            total += jdbcTemplate.update(sql, *params.toTypedArray())
        }
        return total
    }
}
