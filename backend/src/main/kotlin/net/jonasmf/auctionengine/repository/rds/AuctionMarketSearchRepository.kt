package net.jonasmf.auctionengine.repository.rds

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate

data class AuctionMarketSearchRequest(
    val selectedConnectedRealmId: Int,
    val selectedDate: LocalDate,
    val selectedHour: Int,
    val commodityConnectedRealmId: Int,
    val commodityDate: LocalDate,
    val commodityHour: Int,
    val localeColumnSuffix: String,
    val page: Int,
    val pageSize: Int,
    val sortBy: String,
    val sortDirection: String,
    val query: String?,
    val qualityIds: List<Int>,
    val itemClassIds: List<Int>,
    val itemSubclassIds: List<Int>,
    val expansionIds: List<Int>,
    val recipeOnly: Boolean?,
    val minPrice: Long?,
    val maxPrice: Long?,
    val minQuantity: Long?,
    val maxQuantity: Long?,
)

data class AuctionMarketSearchResult(
    val rows: List<AuctionMarketRow>,
    val totalItems: Long,
)

data class AuctionMarketRow(
    val itemId: Int,
    val itemName: String,
    val itemMediaUrl: String?,
    val qualityId: Int?,
    val qualityType: String?,
    val qualityName: String?,
    val itemClassId: Int?,
    val itemClassName: String?,
    val itemSubclassId: Int?,
    val itemSubclassName: String?,
    val recipeId: Int?,
    val recipeName: String?,
    val recipeMediaUrl: String?,
    val selectedBonusKey: String,
    val selectedModifierKey: String,
    val selectedPetSpeciesId: Int,
    val selectedPrice: Long?,
    val selectedP25Price: Long?,
    val selectedP75Price: Long?,
    val selectedQuantity: Long?,
    val commodityPrice: Long?,
    val commodityP25Price: Long?,
    val commodityP75Price: Long?,
    val commodityQuantity: Long?,
)

data class AuctionMarketFilterOptionRow(
    val id: String,
    val label: String,
    val parentId: String? = null,
)

@Repository
class AuctionMarketSearchRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val logger = LoggerFactory.getLogger(AuctionMarketSearchRepository::class.java)

    private val sortColumns =
        mapOf(
            "itemName" to "item_name",
            "quality" to "quality_name",
            "itemClass" to "item_class_name",
            "itemSubclass" to "item_subclass_name",
            "selectedPrice" to "selected_price",
            "commodityPrice" to "commodity_price",
            "selectedQuantity" to "selected_quantity",
            "commodityQuantity" to "commodity_quantity",
        )

    fun search(request: AuctionMarketSearchRequest): AuctionMarketSearchResult {
        val totalStartNanos = System.nanoTime()
        val params = ArrayList<Any?>()
        val (withSql, fromSql) = buildWithAndFromSql(request, params)
        val whereSql = buildWhereSql(request, params)
        val offset = request.page * request.pageSize
        params.add(request.pageSize)
        params.add(offset)

        val sql = buildSearchPagedSql(request, withSql, fromSql, whereSql)
        val queryStartNanos = System.nanoTime()
        val pairs =
            jdbcTemplate.query(
                sql,
                rowMapperWithTotal,
                *params.toTypedArray(),
            )
        val queryMs = elapsedMs(queryStartNanos)
        val totalItems = pairs.firstOrNull()?.second ?: 0L
        val rows = pairs.map { it.first }

        logger.info(
            "Auction market search repository completed in {}ms (requestId={} query={}ms selectedRealm={} selectedDate={} selectedHour={} commodityRealm={} commodityDate={} commodityHour={} totalItems={} returnedRows={})",
            elapsedMs(totalStartNanos),
            requestId(),
            queryMs,
            request.selectedConnectedRealmId,
            request.selectedDate,
            request.selectedHour,
            request.commodityConnectedRealmId,
            request.commodityDate,
            request.commodityHour,
            totalItems,
            rows.size,
        )

        return AuctionMarketSearchResult(rows = rows, totalItems = totalItems)
    }

    fun qualityOptions(request: AuctionMarketSearchRequest): List<AuctionMarketFilterOptionRow> =
        jdbcTemplate.query(
            """
            SELECT
                CAST(iq.internal_id AS CHAR) AS id,
                COALESCE(l.${request.localeColumnSuffix}, l.en_gb, l.en_us, iq.type, CAST(iq.internal_id AS CHAR)) AS label,
                NULL AS parent_id
            FROM item_quality iq
                LEFT JOIN locale l ON l.id = iq.name_id
            ORDER BY label
            """.trimIndent(),
            filterOptionRowMapper,
        )

    fun itemClassOptions(request: AuctionMarketSearchRequest): List<AuctionMarketFilterOptionRow> =
        jdbcTemplate.query(
            """
            SELECT
                CAST(ic.id AS CHAR) AS id,
                COALESCE(l.${request.localeColumnSuffix}, l.en_gb, l.en_us, CAST(ic.id AS CHAR)) AS label,
                NULL AS parent_id
            FROM item_class ic
                LEFT JOIN locale l ON l.id = ic.name_id
            ORDER BY label
            """.trimIndent(),
            filterOptionRowMapper,
        )

    fun itemSubclassOptions(request: AuctionMarketSearchRequest): List<AuctionMarketFilterOptionRow> =
        jdbcTemplate.query(
            """
            SELECT
                CAST(isc.subclass_id AS CHAR) AS id,
                COALESCE(l.${request.localeColumnSuffix}, l.en_gb, l.en_us, CAST(isc.subclass_id AS CHAR)) AS label,
                CAST(isc.class_id AS CHAR) AS parent_id
            FROM item_subclass isc
                LEFT JOIN locale l ON l.id = isc.display_name_id
            ORDER BY label
            """.trimIndent(),
            filterOptionRowMapper,
        )

    fun expansionOptions(request: AuctionMarketSearchRequest): List<AuctionMarketFilterOptionRow> =
        jdbcTemplate.query(
            """
            SELECT
                CAST(e.id AS CHAR) AS id,
                COALESCE(l.${request.localeColumnSuffix}, l.en_gb, l.en_us, e.slug) AS label,
                NULL AS parent_id
            FROM expansion e
                LEFT JOIN locale l ON l.id = e.name_id
            ORDER BY e.display_order, e.id
            """.trimIndent(),
            filterOptionRowMapper,
        )

    private fun buildSearchPagedSql(
        request: AuctionMarketSearchRequest,
        withSql: String,
        fromSql: String,
        whereSql: String,
    ): String =
        """
        $withSql
        SELECT
            wrapped.item_id,
            wrapped.item_name,
            wrapped.item_media_url,
            wrapped.quality_id,
            wrapped.quality_type,
            wrapped.quality_name,
            wrapped.item_class_id,
            wrapped.item_class_name,
            wrapped.item_subclass_id,
            wrapped.item_subclass_name,
            wrapped.recipe_id,
            wrapped.recipe_name,
            wrapped.recipe_media_url,
            wrapped.selected_bonus_key,
            wrapped.selected_modifier_key,
            wrapped.selected_pet_species_id,
            wrapped.selected_price,
            wrapped.selected_p25_price,
            wrapped.selected_p75_price,
            wrapped.selected_quantity,
            wrapped.commodity_price,
            wrapped.commodity_p25_price,
            wrapped.commodity_p75_price,
            wrapped.commodity_quantity,
            wrapped.total_items
        FROM (
            SELECT
                d.item_id,
                COALESCE(d.item_name_${request.localeColumnSuffix},
                d.item_name_en_gb, d.item_name_en_us) AS item_name,
                d.item_media_url,
                d.quality_id,
                d.quality_type,
                COALESCE(d.quality_name_${request.localeColumnSuffix},
                d.quality_name_en_gb, d.quality_name_en_us) AS quality_name,
                d.item_class_id,
                COALESCE(d.item_class_name_${request.localeColumnSuffix}, d.item_class_name_en_gb, d.item_class_name_en_us) AS item_class_name,
                d.item_subclass_id,
                COALESCE(d.item_subclass_name_${request.localeColumnSuffix}, d.item_subclass_name_en_gb, d.item_subclass_name_en_us) AS item_subclass_name,
                d.recipe_id,
                COALESCE(d.recipe_name_${request.localeColumnSuffix}, d.recipe_name_en_gb, d.recipe_name_en_us) AS recipe_name,
                d.recipe_media_url,
                p.selected_bonus_key,
                p.selected_modifier_key,
                p.selected_pet_species_id,
                p.selected_price,
                p.selected_p25_price,
                p.selected_p75_price,
                p.selected_quantity,
                p.commodity_price,
                p.commodity_p25_price,
                p.commodity_p75_price,
                p.commodity_quantity,
                COUNT(*) OVER () AS total_items
            $fromSql
            $whereSql
        ) wrapped
        ${buildOrderBySql(request)}
        LIMIT ? OFFSET ?
        """.trimIndent()

    /**
     * Single listing price/quantity for sort: realm and commodity are mutually exclusive in the UI,
     * so `selectedPrice` / `commodityPrice` (and quantity counterparts) share the same ORDER BY.
     */
    private fun buildOrderBySql(request: AuctionMarketSearchRequest): String {
        val dir = if (request.sortDirection.equals("desc", ignoreCase = true)) "DESC" else "ASC"
        val primary =
            when (request.sortBy) {
                "selectedPrice", "commodityPrice" -> {
                    val expr = "COALESCE(wrapped.selected_price, wrapped.commodity_price)"
                    // MariaDB does not support NULLS LAST; put nulls last via IS NULL sort key.
                    "(($expr) IS NULL) ASC, $expr $dir"
                }
                "selectedQuantity", "commodityQuantity" -> {
                    val expr = "COALESCE(wrapped.selected_quantity, wrapped.commodity_quantity)"
                    "(($expr) IS NULL) ASC, $expr $dir"
                }
                else -> {
                    val col = sortColumns[request.sortBy] ?: sortColumns.getValue("itemName")
                    "wrapped.$col $dir"
                }
            }
        return "ORDER BY $primary, wrapped.item_name ASC, wrapped.item_id ASC"
    }

    /**
     * Rank selected and commodity current snapshots together, then pivot to one row per item.
     * This keeps commodity-only listings visible without making MariaDB expand the same CTEs
     * once for the item union and again for the selected/commodity joins.
     */
    private fun buildWithAndFromSql(
        request: AuctionMarketSearchRequest,
        params: MutableList<Any?>,
    ): Pair<String, String> {
        val withSql = buildCurrentAuctionSnapshotCtes(request, params)
        val fromSql =
            """
            FROM market_prices p
                     STRAIGHT_JOIN v_auction_market_item_details d ON d.item_id = p.item_id
            """.trimIndent()
        return Pair(withSql, fromSql)
    }

    /**
     * When set, quality/class/subclass/recipe filters are applied inside this subquery (via `item`)
     * so ranking touches fewer auction rows before joining the heavy view.
     */
    private fun pushesItemDimensionFiltersIntoAuctionSnapshot(request: AuctionMarketSearchRequest): Boolean =
        request.qualityIds.isNotEmpty() ||
            request.itemClassIds.isNotEmpty() ||
            request.itemSubclassIds.isNotEmpty() ||
            request.expansionIds.isNotEmpty() ||
            request.recipeOnly == true

    private fun buildCurrentAuctionSnapshotCtes(
        request: AuctionMarketSearchRequest,
        params: MutableList<Any?>,
    ): String {
        val useItemJoin = pushesItemDimensionFiltersIntoAuctionSnapshot(request)
        val itemJoin =
            if (useItemJoin) {
                "INNER JOIN v_item i ON i.id = a.item_id\n                "
            } else {
                ""
            }
        val itemFilterSql = if (useItemJoin) buildItemDimensionFilterSql(request) else ""
        params.add(request.selectedConnectedRealmId)
        params.add(request.commodityConnectedRealmId)
        if (useItemJoin) {
            appendItemDimensionFilterParams(params, request)
        }
        return """
            WITH
            latest_history AS (
                SELECT 'sel' AS side, connected_realm_id, MAX(id) AS update_history_id
                FROM connected_realm_update_history
                WHERE connected_realm_id = ?
                GROUP BY connected_realm_id
                UNION ALL
                SELECT 'com' AS side, connected_realm_id, MAX(id) AS update_history_id
                FROM connected_realm_update_history
                WHERE connected_realm_id = ?
                GROUP BY connected_realm_id
            ),
            auction_base AS (
                SELECT
                    lh.side,
                    a.item_id,
                    a.bonus_key,
                    a.modifier_key,
                    COALESCE(a.pet_species_id, -1) AS pet_species_id,
                    a.buyout AS price,
                    a.p25 AS p25_price,
                    a.p75 AS p75_price,
                    a.quantity AS quantity
                FROM auction a
                    INNER JOIN latest_history lh
                        ON lh.connected_realm_id = a.connected_realm_id
                        AND lh.update_history_id = a.update_history_id
                $itemJoin
                WHERE a.buyout IS NOT NULL
                $itemFilterSql
            ),
            auction_ranked AS (
                SELECT
                    side,
                    item_id,
                    bonus_key,
                    modifier_key,
                    pet_species_id,
                    price,
                    p25_price,
                    p75_price,
                    quantity,
                    ROW_NUMBER() OVER (
                        PARTITION BY side, item_id
                        ORDER BY price ASC, bonus_key, modifier_key, pet_species_id
                    ) AS rn
                FROM auction_base
            ),
            current_auction AS (
                SELECT side, item_id, bonus_key, modifier_key, pet_species_id, price, p25_price, p75_price, quantity
                FROM auction_ranked
                WHERE rn = 1
            ),
            market_prices AS (
                SELECT
                    item_id,
                    COALESCE(
                        MAX(CASE WHEN side = 'sel' THEN bonus_key END),
                        MAX(CASE WHEN side = 'com' THEN bonus_key END),
                        ''
                    ) AS selected_bonus_key,
                    COALESCE(
                        MAX(CASE WHEN side = 'sel' THEN modifier_key END),
                        MAX(CASE WHEN side = 'com' THEN modifier_key END),
                        ''
                    ) AS selected_modifier_key,
                    COALESCE(
                        MAX(CASE WHEN side = 'sel' THEN pet_species_id END),
                        MAX(CASE WHEN side = 'com' THEN pet_species_id END),
                        -1
                    ) AS selected_pet_species_id,
                    MAX(CASE WHEN side = 'sel' THEN price END) AS selected_price,
                    MAX(CASE WHEN side = 'sel' THEN p25_price END) AS selected_p25_price,
                    MAX(CASE WHEN side = 'sel' THEN p75_price END) AS selected_p75_price,
                    MAX(CASE WHEN side = 'sel' THEN quantity END) AS selected_quantity,
                    MAX(CASE WHEN side = 'com' THEN price END) AS commodity_price,
                    MAX(CASE WHEN side = 'com' THEN p25_price END) AS commodity_p25_price,
                    MAX(CASE WHEN side = 'com' THEN p75_price END) AS commodity_p75_price,
                    MAX(CASE WHEN side = 'com' THEN quantity END) AS commodity_quantity
                FROM current_auction
                GROUP BY item_id
            )
            """.trimIndent()
    }

    /** IDs match `d.item_subclass_id` (subclass_id), not `item.item_subclass_id` (internal_id). */
    private fun buildItemDimensionFilterSql(request: AuctionMarketSearchRequest): String {
        val parts = mutableListOf<String>()
        if (request.qualityIds.isNotEmpty()) {
            parts.add("i.quality_id IN (${request.qualityIds.joinToString(",") { "?" }})")
        }
        if (request.itemClassIds.isNotEmpty()) {
            parts.add("i.item_class_id IN (${request.itemClassIds.joinToString(",") { "?" }})")
        }
        if (request.itemSubclassIds.isNotEmpty()) {
            val placeholders = request.itemSubclassIds.joinToString(",") { "?" }
            parts.add(
                "EXISTS (SELECT 1 FROM item_subclass isc WHERE isc.internal_id = i.item_subclass_id AND isc.class_id <=> i.item_class_id AND isc.subclass_id IN ($placeholders))",
            )
        }
        if (request.expansionIds.isNotEmpty()) {
            parts.add("i.expansion_id IN (${request.expansionIds.joinToString(",") { "?" }})")
        }
        if (request.recipeOnly == true) {
            parts.add("EXISTS (SELECT 1 FROM recipe r WHERE r.crafted_item_id = i.id)")
        }
        return if (parts.isEmpty()) "" else "  AND " + parts.joinToString(" AND ")
    }

    private fun appendItemDimensionFilterParams(
        params: MutableList<Any?>,
        request: AuctionMarketSearchRequest,
    ) {
        if (request.qualityIds.isNotEmpty()) params.addAll(request.qualityIds)
        if (request.itemClassIds.isNotEmpty()) params.addAll(request.itemClassIds)
        if (request.itemSubclassIds.isNotEmpty()) params.addAll(request.itemSubclassIds)
        if (request.expansionIds.isNotEmpty()) params.addAll(request.expansionIds)
    }

    private fun buildWhereSql(
        request: AuctionMarketSearchRequest,
        params: MutableList<Any?>,
    ): String {
        val predicates = mutableListOf<String>()
        val itemNameColumn =
            "COALESCE(d.item_name_${request.localeColumnSuffix}, d.item_name_en_gb, d.item_name_en_us)"
        val recipeNameColumn =
            "COALESCE(d.recipe_name_${request.localeColumnSuffix}, d.recipe_name_en_gb, d.recipe_name_en_us)"

        request.query?.trim()?.takeIf { it.isNotEmpty() }?.let {
            predicates.add("($itemNameColumn LIKE ? ESCAPE '!' OR $recipeNameColumn LIKE ? ESCAPE '!')")
            val like = "%${it.escapeLike()}%"
            params.add(like)
            params.add(like)
        }
        if (!pushesItemDimensionFiltersIntoAuctionSnapshot(request)) {
            addInPredicate("d.quality_id", request.qualityIds, predicates, params)
            addInPredicate("d.item_class_id", request.itemClassIds, predicates, params)
            addInPredicate("d.item_subclass_id", request.itemSubclassIds, predicates, params)
            addInPredicate("d.expansion_id", request.expansionIds, predicates, params)
            if (request.recipeOnly == true) {
                predicates.add("d.recipe_id IS NOT NULL")
            }
        }
        request.minPrice?.let {
            predicates.add("COALESCE(p.selected_price, p.commodity_price) >= ?")
            params.add(it)
        }
        request.maxPrice?.let {
            predicates.add("COALESCE(p.selected_price, p.commodity_price) <= ?")
            params.add(it)
        }
        request.minQuantity?.let {
            predicates.add("COALESCE(p.selected_quantity, p.commodity_quantity) >= ?")
            params.add(it)
        }
        request.maxQuantity?.let {
            predicates.add("COALESCE(p.selected_quantity, p.commodity_quantity) <= ?")
            params.add(it)
        }

        return if (predicates.isEmpty()) "" else predicates.joinToString(prefix = "WHERE ", separator = " AND ")
    }

    private fun addInPredicate(
        column: String,
        values: List<Int>,
        predicates: MutableList<String>,
        params: MutableList<Any?>,
    ) {
        if (values.isEmpty()) return
        predicates.add("$column IN (${values.joinToString(",") { "?" }})")
        params.addAll(values)
    }

    private fun String.escapeLike(): String =
        replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private val rowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            AuctionMarketRow(
                itemId = rs.getInt("item_id"),
                itemName = rs.getString("item_name"),
                itemMediaUrl = rs.getString("item_media_url"),
                qualityId = rs.getNullableInt("quality_id"),
                qualityType = rs.getString("quality_type"),
                qualityName = rs.getString("quality_name"),
                itemClassId = rs.getNullableInt("item_class_id"),
                itemClassName = rs.getString("item_class_name"),
                itemSubclassId = rs.getNullableInt("item_subclass_id"),
                itemSubclassName = rs.getString("item_subclass_name"),
                recipeId = rs.getNullableInt("recipe_id"),
                recipeName = rs.getString("recipe_name"),
                recipeMediaUrl = rs.getString("recipe_media_url"),
                selectedBonusKey = rs.getString("selected_bonus_key") ?: "",
                selectedModifierKey = rs.getString("selected_modifier_key") ?: "",
                selectedPetSpeciesId = rs.getInt("selected_pet_species_id"),
                selectedPrice = rs.getNullableLong("selected_price"),
                selectedP25Price = rs.getNullableLong("selected_p25_price"),
                selectedP75Price = rs.getNullableLong("selected_p75_price"),
                selectedQuantity = rs.getNullableLong("selected_quantity"),
                commodityPrice = rs.getNullableLong("commodity_price"),
                commodityP25Price = rs.getNullableLong("commodity_p25_price"),
                commodityP75Price = rs.getNullableLong("commodity_p75_price"),
                commodityQuantity = rs.getNullableLong("commodity_quantity"),
            )
        }

    private val rowMapperWithTotal =
        RowMapper { rs: ResultSet, rowNum: Int ->
            val totalItems = rs.getLong("total_items")
            val row = requireNotNull(rowMapper.mapRow(rs, rowNum)) { "market search row expected" }
            row to totalItems
        }

    private val filterOptionRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            AuctionMarketFilterOptionRow(
                id = rs.getString("id"),
                label = rs.getString("label") ?: rs.getString("id"),
                parentId = rs.getString("parent_id"),
            )
        }

    private fun ResultSet.getNullableInt(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    private fun requestId(): String = MDC.get("requestId") ?: "-"

    /** For integration tests: `EXPLAIN` / `EXPLAIN ANALYZE` against real MariaDB. */
    internal fun buildMarketSearchPagedSqlForExplain(request: AuctionMarketSearchRequest): Pair<String, Array<Any?>> {
        val params = ArrayList<Any?>()
        val (withSql, fromSql) = buildWithAndFromSql(request, params)
        val whereSql = buildWhereSql(request, params)
        val offset = request.page * request.pageSize
        params.add(request.pageSize)
        params.add(offset)
        return Pair(
            buildSearchPagedSql(request, withSql, fromSql, whereSql),
            params.toTypedArray(),
        )
    }

    internal fun buildQualityOptionsSqlForExplain(request: AuctionMarketSearchRequest): Pair<String, Array<Any?>> =
        Pair(
            """
            SELECT
                CAST(iq.internal_id AS CHAR) AS id,
                COALESCE(l.${request.localeColumnSuffix}, l.en_gb, l.en_us, iq.type, CAST(iq.internal_id AS CHAR)) AS label,
                NULL AS parent_id
            FROM item_quality iq
                LEFT JOIN locale l ON l.id = iq.name_id
            ORDER BY label ASC
            """.trimIndent(),
            emptyArray(),
        )

    internal fun buildItemClassOptionsSqlForExplain(request: AuctionMarketSearchRequest): Pair<String, Array<Any?>> =
        Pair(
            """
            SELECT
                CAST(ic.id AS CHAR) AS id,
                COALESCE(l.${request.localeColumnSuffix}, l.en_gb, l.en_us, CAST(ic.id AS CHAR)) AS label,
                NULL AS parent_id
            FROM item_class ic
                LEFT JOIN locale l ON l.id = ic.name_id
            ORDER BY label ASC
            """.trimIndent(),
            emptyArray(),
        )

    internal fun buildItemSubclassOptionsSqlForExplain(request: AuctionMarketSearchRequest): Pair<String, Array<Any?>> =
        Pair(
            """
            SELECT
                CAST(isc.subclass_id AS CHAR) AS id,
                COALESCE(l.${request.localeColumnSuffix}, l.en_gb, l.en_us, CAST(isc.subclass_id AS CHAR)) AS label,
                CAST(isc.class_id AS CHAR) AS parent_id
            FROM item_subclass isc
                LEFT JOIN locale l ON l.id = isc.display_name_id
            ORDER BY label ASC
            """.trimIndent(),
            emptyArray(),
        )
}
