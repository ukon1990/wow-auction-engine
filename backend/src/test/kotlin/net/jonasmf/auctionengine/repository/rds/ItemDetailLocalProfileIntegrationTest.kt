package net.jonasmf.auctionengine.repository.rds

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.sql.Date
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Manual profiling against developer MariaDB on localhost:59000.
 * Run: ./mvnw test -Dtest=ItemDetailLocalProfileIntegrationTest
 */
@Disabled("Manual local DB profiling only")
@SpringBootTest
@ActiveProfiles("default")
class ItemDetailLocalProfileIntegrationTest {
    @Autowired
    private lateinit var repository: AuctionMarketItemDetailRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `profile draenor item detail and crafting analytics queries`() {
        val ctx = loadDraenorContext()
        val itemId = 169451
        val recipeId = 40898
        val from = ctx.selectedDate.minusDays(13)
        val to = ctx.selectedDate

        profile("loadCraftings") {
            repository.loadCraftings(
                connectedRealmId = ctx.selectedConnectedRealmId,
                commodityConnectedRealmId = ctx.commodityConnectedRealmId,
                itemId = itemId,
                statDate = ctx.selectedDate,
                commodityStatDate = ctx.commodityDate,
                hourOfDay = ctx.selectedHour,
                commodityHourOfDay = ctx.commodityHour,
                variant = false,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = -1,
                preferredRecipeId = recipeId,
                localeColumnSuffix = "en_gb",
            )
        }

        val craftingRows =
            repository.loadCraftings(
                connectedRealmId = ctx.selectedConnectedRealmId,
                commodityConnectedRealmId = ctx.commodityConnectedRealmId,
                itemId = itemId,
                statDate = ctx.selectedDate,
                commodityStatDate = ctx.commodityDate,
                hourOfDay = ctx.selectedHour,
                commodityHourOfDay = ctx.commodityHour,
                variant = false,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = -1,
                preferredRecipeId = recipeId,
                localeColumnSuffix = "en_gb",
            )
        profile("loadCraftingReagents") {
            repository.loadCraftingReagents(
                connectedRealmId = ctx.selectedConnectedRealmId,
                commodityConnectedRealmId = ctx.commodityConnectedRealmId,
                craftedItemId = itemId,
                recipeIds = craftingRows.map { it.recipeId },
                statDate = ctx.selectedDate,
                commodityStatDate = ctx.commodityDate,
                hourOfDay = ctx.selectedHour,
                commodityHourOfDay = ctx.commodityHour,
                localeColumnSuffix = "en_gb",
            )
        }

        profile("loadDailySeries realm") {
            repository.loadDailySeries(
                connectedRealmId = ctx.selectedConnectedRealmId,
                itemId = itemId,
                fromDate = from,
                toDate = to,
                variant = false,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = -1,
            )
        }

        profile("loadCraftingAnalyticsDaily") {
            repository.loadCraftingAnalyticsDaily(
                connectedRealmId = ctx.selectedConnectedRealmId,
                commodityConnectedRealmId = ctx.commodityConnectedRealmId,
                itemId = itemId,
                recipeId = recipeId,
                fromDate = from,
                toDate = to,
                hourOfDay = ctx.selectedHour,
                commodityHourOfDay = ctx.commodityHour,
                variant = false,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = -1,
            )
        }

        profile("loadCraftingAnalyticsHeatmap") {
            repository.loadCraftingAnalyticsHeatmap(
                connectedRealmId = ctx.selectedConnectedRealmId,
                commodityConnectedRealmId = ctx.commodityConnectedRealmId,
                itemId = itemId,
                recipeId = recipeId,
                fromDate = from,
                toDate = to,
                variant = false,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = -1,
            )
        }
    }

    private fun profile(
        label: String,
        block: () -> Unit,
    ) {
        val start = System.nanoTime()
        block()
        println("$label wallMs=${(System.nanoTime() - start) / 1_000_000}")
    }

    private fun loadDraenorContext(): DraenorContext {
        val row =
            jdbcTemplate.queryForMap(
                """
                SELECT cr.id AS selectedConnectedRealmId, r.timezone,
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
        val selectedSnapshot = snapshotFrom((row["selectedLastModified"] as java.sql.Timestamp).toInstant(), row["timezone"] as String)
        val commoditySnapshot = snapshotFrom((row["commodityLastModified"] as java.sql.Timestamp).toInstant(), "UTC")
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
