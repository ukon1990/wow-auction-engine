package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.domain.item.Item
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val ITEM_JDBC_CHUNK_SIZE = 200

data class ItemPersistenceSummary(
    val localesUpserted: Int,
    val itemQualitiesUpserted: Int,
    val inventoryTypesUpserted: Int,
    val itemBindingsUpserted: Int,
    val itemClassesUpserted: Int,
    val itemSubclassesUpserted: Int,
    val itemAppearanceReferencesUpserted: Int,
    val itemsUpserted: Int,
    val itemAppearanceLinksUpserted: Int,
)

data class ItemSourceDiscovery(
    val auctionSourceCount: Int,
    val recipeCraftedSourceCount: Int,
    val recipeReagentSourceCount: Int,
    val candidateItemCount: Int,
    val existingItemCount: Int,
    val missingItemIds: List<Int>,
)

data class ExpansionRangeItemDiscovery(
    val candidateItemCount: Int,
    val existingItemCount: Int,
    val missingItemIds: List<Int>,
)

data class ItemFetchFailureState(
    val itemId: Int,
    val failureCount: Int,
    val lastErrorStatus: String?,
    val lastErrorMessage: String?,
    val lastFailedAt: OffsetDateTime,
    val nextRetryAt: OffsetDateTime?,
    val manualDisabled: Boolean,
)

data class ItemRetryEligibility(
    val retryableIds: List<Int>,
    val cooldownSkippedIds: List<Int>,
    val manualDisabledIds: List<Int>,
)

@Repository
class ItemJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val itemPersistenceWriter = ItemJdbcPersistenceWriter(jdbcTemplate)

    fun findItemFetchFailureStates(itemIds: Collection<Int>): Map<Int, ItemFetchFailureState> {
        if (itemIds.isEmpty()) return emptyMap()
        val states = linkedMapOf<Int, ItemFetchFailureState>()
        itemIds
            .distinct()
            .chunked(ITEM_JDBC_CHUNK_SIZE)
            .forEach { chunk ->
                jdbcTemplate.query(
                    """
                    SELECT
                        item_id,
                        failure_count,
                        last_error_status,
                        last_error_message,
                        last_failed_at,
                        next_retry_at,
                        manual_disabled
                    FROM item_fetch_failure
                    WHERE item_id IN (${placeholders(chunk.size)})
                    """.trimIndent(),
                    { resultSet, _ ->
                        ItemFetchFailureState(
                            itemId = resultSet.getInt("item_id"),
                            failureCount = resultSet.getInt("failure_count"),
                            lastErrorStatus = resultSet.getString("last_error_status"),
                            lastErrorMessage = resultSet.getString("last_error_message"),
                            lastFailedAt = resultSet.getObject("last_failed_at", OffsetDateTime::class.java),
                            nextRetryAt = resultSet.getObject("next_retry_at", OffsetDateTime::class.java),
                            manualDisabled = resultSet.getBoolean("manual_disabled"),
                        )
                    },
                    *chunk.toTypedArray(),
                ).forEach { state -> states[state.itemId] = state }
            }
        return states
    }

    fun classifyItemRetryEligibility(
        itemIds: Collection<Int>,
        now: OffsetDateTime,
    ): ItemRetryEligibility {
        if (itemIds.isEmpty()) {
            return ItemRetryEligibility(
                retryableIds = emptyList(),
                cooldownSkippedIds = emptyList(),
                manualDisabledIds = emptyList(),
            )
        }
        val states = findItemFetchFailureStates(itemIds)
        val retryable = mutableListOf<Int>()
        val cooldownSkipped = mutableListOf<Int>()
        val manualDisabled = mutableListOf<Int>()

        itemIds.distinct().forEach { itemId ->
            val state = states[itemId]
            when {
                state == null -> retryable += itemId
                state.manualDisabled -> manualDisabled += itemId
                state.nextRetryAt != null && state.nextRetryAt.isAfter(now) -> cooldownSkipped += itemId
                else -> retryable += itemId
            }
        }

        return ItemRetryEligibility(
            retryableIds = retryable,
            cooldownSkippedIds = cooldownSkipped,
            manualDisabledIds = manualDisabled,
        )
    }

    fun upsertItemFetchFailureState(
        itemId: Int,
        failureCount: Int,
        lastErrorStatus: String?,
        lastErrorMessage: String?,
        lastFailedAt: OffsetDateTime,
        nextRetryAt: OffsetDateTime?,
        manualDisabled: Boolean,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO item_fetch_failure (
                item_id,
                failure_count,
                last_error_status,
                last_error_message,
                last_failed_at,
                next_retry_at,
                manual_disabled
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                failure_count = VALUES(failure_count),
                last_error_status = VALUES(last_error_status),
                last_error_message = VALUES(last_error_message),
                last_failed_at = VALUES(last_failed_at),
                next_retry_at = VALUES(next_retry_at),
                manual_disabled = VALUES(manual_disabled)
            """.trimIndent(),
            itemId,
            failureCount,
            lastErrorStatus,
            lastErrorMessage,
            lastFailedAt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
            nextRetryAt?.withOffsetSameInstant(ZoneOffset.UTC)?.toLocalDateTime(),
            manualDisabled,
        )
    }

    fun clearItemFetchFailureStates(itemIds: Collection<Int>) {
        if (itemIds.isEmpty()) return
        itemIds
            .distinct()
            .chunked(ITEM_JDBC_CHUNK_SIZE)
            .forEach { chunk ->
                jdbcTemplate.update(
                    "DELETE FROM item_fetch_failure WHERE item_id IN (${placeholders(chunk.size)})",
                    *chunk.toTypedArray(),
                )
            }
    }

    fun findMissingItemIdsForDate(date: LocalDate): ItemSourceDiscovery {
        val missingItemIds = mutableListOf<Int>()
        var auctionSourceCount = 0
        var recipeCraftedSourceCount = 0
        var recipeReagentSourceCount = 0
        var candidateItemCount = 0
        var existingItemCount = 0

        jdbcTemplate.query(
            """
            WITH auction_source AS (
                SELECT item_id
                FROM auction_stats_hourly
                WHERE date = ?
                  AND item_id > 0
                  AND (pet_species_id IS NULL OR pet_species_id IN (-1, 0))
                GROUP BY item_id
            ),
            crafted_source AS (
                SELECT crafted_item_id AS item_id
                FROM v_recipe_crafted_output
                WHERE crafted_item_id IS NOT NULL
                GROUP BY crafted_item_id
            ),
            reagent_source AS (
                SELECT item_id
                FROM v_recipe_reagent
                WHERE item_id IS NOT NULL
                GROUP BY item_id
            ),
            source_ids AS (
                SELECT item_id, 1 AS from_auction, 0 AS from_crafted, 0 AS from_reagent FROM auction_source
                UNION ALL
                SELECT item_id, 0 AS from_auction, 1 AS from_crafted, 0 AS from_reagent FROM crafted_source
                UNION ALL
                SELECT item_id, 0 AS from_auction, 0 AS from_crafted, 1 AS from_reagent FROM reagent_source
            ),
            aggregated AS (
                SELECT
                    item_id,
                    MAX(from_auction) AS from_auction,
                    MAX(from_crafted) AS from_crafted,
                    MAX(from_reagent) AS from_reagent
                FROM source_ids
                GROUP BY item_id
            ),
            classified AS (
                SELECT
                    aggregated.item_id,
                    aggregated.from_auction,
                    aggregated.from_crafted,
                    aggregated.from_reagent,
                    CASE WHEN existing_item.id IS NULL THEN 1 ELSE 0 END AS is_missing
                FROM aggregated
                LEFT JOIN (
                    SELECT DISTINCT id
                    FROM `item`
                ) existing_item ON existing_item.id = aggregated.item_id
            ),
            summary AS (
                SELECT
                    COALESCE(SUM(from_auction), 0) AS auction_source_count,
                    COALESCE(SUM(from_crafted), 0) AS recipe_crafted_source_count,
                    COALESCE(SUM(from_reagent), 0) AS recipe_reagent_source_count,
                    COUNT(*) AS candidate_item_count,
                    COALESCE(SUM(CASE WHEN is_missing = 0 THEN 1 ELSE 0 END), 0) AS existing_item_count,
                    COALESCE(SUM(is_missing), 0) AS missing_item_count
                FROM classified
            )
            SELECT
                sort_order,
                item_id,
                auction_source_count,
                recipe_crafted_source_count,
                recipe_reagent_source_count,
                candidate_item_count,
                existing_item_count,
                missing_item_count
            FROM (
                SELECT
                    0 AS sort_order,
                    NULL AS item_id,
                    auction_source_count,
                    recipe_crafted_source_count,
                    recipe_reagent_source_count,
                    candidate_item_count,
                    existing_item_count,
                    missing_item_count
                FROM summary
                UNION ALL
                SELECT
                    1 AS sort_order,
                    classified.item_id,
                    NULL AS auction_source_count,
                    NULL AS recipe_crafted_source_count,
                    NULL AS recipe_reagent_source_count,
                    NULL AS candidate_item_count,
                    NULL AS existing_item_count,
                    NULL AS missing_item_count
                FROM classified
                WHERE is_missing = 1
            ) result
            ORDER BY sort_order, item_id
            """.trimIndent(),
            { resultSet ->
                if (resultSet.getInt("sort_order") == 0) {
                    auctionSourceCount = resultSet.getInt("auction_source_count")
                    recipeCraftedSourceCount = resultSet.getInt("recipe_crafted_source_count")
                    recipeReagentSourceCount = resultSet.getInt("recipe_reagent_source_count")
                    candidateItemCount = resultSet.getInt("candidate_item_count")
                    existingItemCount = resultSet.getInt("existing_item_count")
                } else {
                    missingItemIds += resultSet.getInt("item_id")
                }
            },
            date,
        )

        return ItemSourceDiscovery(
            auctionSourceCount = auctionSourceCount,
            recipeCraftedSourceCount = recipeCraftedSourceCount,
            recipeReagentSourceCount = recipeReagentSourceCount,
            candidateItemCount = candidateItemCount,
            existingItemCount = existingItemCount,
            missingItemIds = missingItemIds,
        )
    }

    fun findExistingItemIds(itemIds: Collection<Int>): Set<Int> {
        if (itemIds.isEmpty()) return emptySet()
        return itemIds
            .distinct()
            .chunked(ITEM_JDBC_CHUNK_SIZE)
            .flatMap { chunk ->
                jdbcTemplate.query(
                    "SELECT DISTINCT id FROM `item` WHERE id IN (${placeholders(chunk.size)})",
                    { resultSet, _ -> resultSet.getInt("id") },
                    *chunk.toTypedArray(),
                )
            }.toSet()
    }

    fun findMissingItemIdsForEnabledExpansionRanges(): ExpansionRangeItemDiscovery {
        val candidateIds = linkedSetOf<Int>()
        jdbcTemplate.query(
            """
            SELECT start_item_id, end_item_id
            FROM expansion_item_range
            WHERE enabled = TRUE
            ORDER BY start_item_id, end_item_id
            """.trimIndent(),
            { resultSet ->
                val start = resultSet.getInt("start_item_id")
                val end = resultSet.getInt("end_item_id")
                for (itemId in start..end) {
                    candidateIds += itemId
                }
            },
        )
        val existingIds = findExistingItemIds(candidateIds)
        return ExpansionRangeItemDiscovery(
            candidateItemCount = candidateIds.size,
            existingItemCount = existingIds.size,
            missingItemIds = candidateIds.filterNot(existingIds::contains),
        )
    }

    @Transactional
    fun syncItems(items: List<Item>): ItemPersistenceSummary = itemPersistenceWriter.syncItems(items)

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")
}
