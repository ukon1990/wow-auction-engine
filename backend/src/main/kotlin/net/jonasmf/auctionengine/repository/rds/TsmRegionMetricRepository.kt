package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.tsm.TsmRegionMetric
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

private const val MAX_PREPARED_STATEMENT_PLACEHOLDERS = 60_000
private const val UPSERT_COLUMN_COUNT = 9

@Repository
class TsmRegionMetricRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional
    fun upsertAll(metrics: Collection<TsmRegionMetric>): Int {
        if (metrics.isEmpty()) return 0

        var totalRows = 0
        metrics
            .sortedWith(
                compareBy(
                    { it.region.name },
                    { it.subjectType.name },
                    { it.subjectId },
                ),
            ).chunked(maxRowsPerStatement(UPSERT_COLUMN_COUNT))
            .forEach { chunk ->
                val sql =
                    """
                    INSERT INTO tsm_region_metric (
                        region,
                        subject_type,
                        subject_id,
                        sale_rate,
                        sold_per_day,
                        market_value,
                        historical,
                        avg_sale_price,
                        source_updated_at
                    ) VALUES ${rowPlaceholders(chunk.size, UPSERT_COLUMN_COUNT)}
                    ON DUPLICATE KEY UPDATE
                        sale_rate = VALUES(sale_rate),
                        sold_per_day = VALUES(sold_per_day),
                        market_value = VALUES(market_value),
                        historical = VALUES(historical),
                        avg_sale_price = VALUES(avg_sale_price),
                        source_updated_at = VALUES(source_updated_at)
                    """.trimIndent()

                val params =
                    ArrayList<Any?>(chunk.size * UPSERT_COLUMN_COUNT).apply {
                        chunk.forEach { metric ->
                            add(metric.region.name)
                            add(metric.subjectType.name)
                            add(metric.subjectId)
                            add(metric.saleRate)
                            add(metric.soldPerDay)
                            add(metric.marketValue)
                            add(metric.historical)
                            add(metric.avgSalePrice)
                            add(Timestamp.from(metric.sourceUpdatedAt))
                        }
                    }
                totalRows += jdbcTemplate.update(sql, *params.toTypedArray())
            }
        return totalRows
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")

    private fun rowPlaceholders(
        rowCount: Int,
        columnCount: Int,
    ): String = List(rowCount) { "(${placeholders(columnCount)})" }.joinToString(",")

    private fun maxRowsPerStatement(columnCount: Int): Int = MAX_PREPARED_STATEMENT_PLACEHOLDERS / columnCount
}
