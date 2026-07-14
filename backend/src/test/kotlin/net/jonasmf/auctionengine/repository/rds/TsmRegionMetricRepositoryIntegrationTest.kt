package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.constant.TsmSubjectType
import net.jonasmf.auctionengine.dbo.rds.tsm.TsmRegionMetric
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.Instant

class TsmRegionMetricRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    lateinit var repository: TsmRegionMetricRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanMetrics() {
        jdbcTemplate.update("DELETE FROM tsm_region_metric")
    }

    @Test
    fun `EU item 39 and pet 39 coexist with distinct sale rates`() {
        val sourceUpdatedAt = Instant.parse("2026-07-14T06:00:00Z")

        repository.upsertAll(
            listOf(
                TsmRegionMetric(
                    region = Region.Europe,
                    subjectType = TsmSubjectType.ITEM,
                    subjectId = 39,
                    saleRate = BigDecimal("0.25000000"),
                    soldPerDay = BigDecimal("1.50000000"),
                    marketValue = 1000L,
                    historical = 2000L,
                    avgSalePrice = 1500L,
                    sourceUpdatedAt = sourceUpdatedAt,
                ),
                TsmRegionMetric(
                    region = Region.Europe,
                    subjectType = TsmSubjectType.PET,
                    subjectId = 39,
                    saleRate = BigDecimal("0.50000000"),
                    soldPerDay = BigDecimal("2.00000000"),
                    marketValue = 5000L,
                    historical = 4000L,
                    avgSalePrice = 4500L,
                    sourceUpdatedAt = sourceUpdatedAt,
                ),
            ),
        )

        val rows =
            jdbcTemplate.query(
                """
                SELECT subject_type, subject_id, sale_rate, sold_per_day, market_value
                FROM tsm_region_metric
                WHERE region = 'Europe' AND subject_id = 39
                ORDER BY subject_type
                """.trimIndent(),
            ) { rs, _ ->
                Triple(
                    rs.getString("subject_type"),
                    rs.getBigDecimal("sale_rate"),
                    rs.getLong("market_value"),
                )
            }

        assertThat(rows).hasSize(2)
        assertThat(rows[0].first).isEqualTo("ITEM")
        assertThat(rows[0].second).isEqualByComparingTo("0.25000000")
        assertThat(rows[0].third).isEqualTo(1000L)
        assertThat(rows[1].first).isEqualTo("PET")
        assertThat(rows[1].second).isEqualByComparingTo("0.50000000")
        assertThat(rows[1].third).isEqualTo(5000L)
    }
}
