package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Region
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Manual profiling against the developer MariaDB on localhost:59000.
 * Run: ./mvnw test -Dtest=MarketSearchLocalProfileIntegrationTest
 */
@org.junit.jupiter.api.Disabled("Manual local DB profiling only")
@SpringBootTest
@ActiveProfiles("default")
class MarketSearchLocalProfileIntegrationTest {
    @Autowired
    private lateinit var auctionMarketSearchRepository: AuctionMarketSearchRepository

    @Autowired
    private lateinit var craftingMarketSearchRepository: CraftingMarketSearchRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `profile draenor auction and crafting search plans`() {
        val context = loadDraenorContext()
        val auctionRequest =
            AuctionMarketSearchRequest(
                region = Region.Europe,
                selectedConnectedRealmId = context.selectedConnectedRealmId,
                selectedDate = context.selectedDate,
                selectedHour = context.selectedHour,
                commodityConnectedRealmId = context.commodityConnectedRealmId,
                commodityDate = context.commodityDate,
                commodityHour = context.commodityHour,
                localeColumnSuffix = "en_gb",
                page = 1,
                pageSize = 25,
                sortBy = "saleRate",
                sortDirection = "asc",
                query = null,
                qualityIds = emptyList(),
                itemClassIds = emptyList(),
                itemSubclassIds = emptyList(),
                expansionIds = emptyList(),
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
                minSaleRatePercent = null,
                maxSaleRatePercent = null,
                minSoldPerDay = null,
                maxSoldPerDay = null,
            )
        val craftingRequest =
            CraftingMarketSearchRequest(
                region = Region.Europe,
                selectedConnectedRealmId = context.selectedConnectedRealmId,
                selectedDate = context.selectedDate,
                selectedHour = context.selectedHour,
                commodityConnectedRealmId = context.commodityConnectedRealmId,
                commodityDate = context.commodityDate,
                commodityHour = context.commodityHour,
                previousDate = context.selectedDate.minusDays(1),
                commodityPreviousDate = context.commodityDate.minusDays(1),
                localeColumnSuffix = "en_gb",
                page = 0,
                pageSize = 25,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                professionIds = emptyList(),
                expansionIds = emptyList(),
                qualityIds = emptyList(),
                minProfit = null,
                maxProfit = null,
                minRoiPercent = null,
                maxRoiPercent = null,
                minSaleRatePercent = null,
                maxSaleRatePercent = null,
                minSoldPerDay = null,
                maxSoldPerDay = null,
                minReagentCost = null,
                maxReagentCost = null,
                minOutputPrice = null,
                maxOutputPrice = null,
                minOutputPriceChangePercent = null,
                maxOutputPriceChangePercent = null,
                requireCompleteReagentPricing = false,
            )

        profile("auction search", auctionMarketSearchRepository.buildMarketSearchPagedSqlForExplain(auctionRequest))
        profile("crafting search", craftingMarketSearchRepository.buildCraftingMarketSearchPagedSqlForExplain(craftingRequest))
    }

    private fun profile(
        label: String,
        sqlAndArgs: Pair<String, Array<Any?>>,
    ) {
        val (sql, args) = sqlAndArgs
        println("\n========== $label ==========")
        val analyzeStart = System.nanoTime()
        val analyzeRows = jdbcTemplate.queryForList("ANALYZE $sql", *args)
        println("ANALYZE wallMs=${(System.nanoTime() - analyzeStart) / 1_000_000}")
        analyzeRows.forEach { println(it) }

        val queryStart = System.nanoTime()
        val rows = jdbcTemplate.queryForList(sql, *args)
        println("Query wallMs=${(System.nanoTime() - queryStart) / 1_000_000} rows=${rows.size}")
    }

    private fun loadDraenorContext(): DraenorContext {
        val row =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    cr.id AS selectedConnectedRealmId,
                    r.timezone AS timezone,
                    ah.last_modified AS selectedLastModified,
                    commodity_cr.id AS commodityConnectedRealmId,
                    commodity_ah.last_modified AS commodityLastModified
                FROM realm r
                JOIN connected_realm_realms crr ON crr.realms_id = r.id
                JOIN connected_realm cr ON cr.id = crr.connected_realm_id
                JOIN auction_house ah ON ah.id = cr.auction_house_id
                JOIN connected_realm commodity_cr ON commodity_cr.id = -2
                JOIN auction_house commodity_ah ON commodity_ah.id = commodity_cr.auction_house_id
                WHERE r.region_id = 2 AND r.slug = 'draenor'
                LIMIT 1
                """.trimIndent(),
            )
        val selectedSnapshot =
            snapshotFrom(
                lastModified = (row["selectedLastModified"] as java.sql.Timestamp).toInstant(),
                timezone = row["timezone"] as String,
            )
        val commoditySnapshot =
            snapshotFrom(
                lastModified = (row["commodityLastModified"] as java.sql.Timestamp).toInstant(),
                timezone = "UTC",
            )
        return DraenorContext(
            selectedConnectedRealmId = (row["selectedConnectedRealmId"] as Number).toInt(),
            selectedDate = selectedSnapshot.first,
            selectedHour = selectedSnapshot.second,
            commodityConnectedRealmId = (row["commodityConnectedRealmId"] as Number).toInt(),
            commodityDate = commoditySnapshot.first,
            commodityHour = commoditySnapshot.second,
        )
    }

    private fun snapshotFrom(
        lastModified: Instant,
        timezone: String,
    ): Pair<LocalDate, Int> {
        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
        val local = lastModified.atZone(zone)
        return local.toLocalDate() to local.hour
    }

    private data class DraenorContext(
        val selectedConnectedRealmId: Int,
        val selectedDate: LocalDate,
        val selectedHour: Int,
        val commodityConnectedRealmId: Int,
        val commodityDate: LocalDate,
        val commodityHour: Int,
    )

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun localMariaDb(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                "jdbc:mariadb://localhost:59000/dbo?serverTimezone=UTC&cachePrepStmts=true&useServerPrepStmts=true"
            }
            registry.add("spring.datasource.username") { "root" }
            registry.add("spring.datasource.password") { "root" }
            registry.add("app.scheduling.enabled") { "false" }
            registry.add("spring.cloud.aws.dynamodb.endpoint") { "http://localhost:4566" }
            registry.add("spring.cloud.aws.s3.endpoint") { "http://localhost:4566" }
        }
    }
}
