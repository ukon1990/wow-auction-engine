package net.jonasmf.auctionengine.auction.repository

import net.jonasmf.auctionengine.auction.ItemQualityOrder
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AuctionSearchRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val logger = LoggerFactory.getLogger(AuctionSearchRepository::class.java)

    fun search(request: AuctionSearchRequest): AuctionSearchResult {
        val totalStartNanos = System.nanoTime()
        val (sql, params) = buildPagedSql(request)
        val queryStartNanos = System.nanoTime()
        var totalItems = 0L
        val rows =
            jdbcTemplate.query(sql, { rs, rowNum ->
                if (rowNum == 0) totalItems = rs.getLong("total_items")
                auctionSearchRowMapper.mapRow(rs, rowNum)
            }, *params)
        if (rows.isEmpty() && request.page > 0) {
            // Page past the end: the window count is not available without rows.
            val countParams = ArrayList<Any?>()
            val countSql = buildCountSql(request, countParams)
            totalItems = jdbcTemplate.queryForObject(countSql, Long::class.java, *countParams.toTypedArray()) ?: 0L
        }
        val queryMs = elapsedMs(queryStartNanos)

        logger.info(
            "Auction market search repository completed in {}ms (correlationId={} query={}ms selectedRealm={} selectedDate={} selectedHour={} commodityRealm={} commodityDate={} commodityHour={} totalItems={} returnedRows={})",
            elapsedMs(totalStartNanos),
            correlationId(),
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

        return AuctionSearchResult(rows = rows, totalItems = totalItems)
    }


    fun qualityOptions(request: AuctionSearchRequest): List<AuctionFilterOptionRow> =
        qualityOptions(request.localeColumnSuffix)

    fun qualityOptions(localeColumnSuffix: String): List<AuctionFilterOptionRow> {
        val qualityOrderSql = ItemQualityOrder.sqlOrderByCase("iq.type")
        val rows =
            jdbcTemplate.query(
                """
                SELECT
                    CAST(iq.internal_id AS CHAR) AS id,
                    COALESCE(l.$localeColumnSuffix, l.en_gb, l.en_us, iq.type, CAST(iq.internal_id AS CHAR)) AS label,
                    NULL AS parent_id,
                    iq.type AS quality_type
                FROM item_quality iq
                    LEFT JOIN locale l ON l.id = iq.name_id
                WHERE iq.type IS NOT NULL
                ORDER BY $qualityOrderSql, iq.internal_id
                """.trimIndent(),
                auctionQualityOptionRowMapper,
            )
        return rows
            .groupBy { it.qualityType?.uppercase() }
            .values
            .mapNotNull { group -> group.minByOrNull { ItemQualityOrder.rank(it.qualityType) } }
            .sortedBy { ItemQualityOrder.rank(it.qualityType) }
    }

    fun itemClassOptions(request: AuctionSearchRequest): List<AuctionFilterOptionRow> =
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
            auctionOptionRowMapper,
        )

    fun itemSubclassOptions(request: AuctionSearchRequest): List<AuctionFilterOptionRow> =
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
            auctionOptionRowMapper,
        )

    fun expansionOptions(request: AuctionSearchRequest): List<AuctionFilterOptionRow> =
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
            auctionOptionRowMapper,
        )

    /**
     * One round trip: filter, sort and page on the cheap columns first (with the total from a window count),
     * then join the wide item details view for the returned page only.
     */
    private fun buildPagedSql(request: AuctionSearchRequest): Pair<String, Array<Any?>> {
        val params = ArrayList<Any?>()
        val withSql = buildCurrentAuctionSnapshotCtes(request, params)
        val itemSource = itemSourceSql(request)
        params.add(request.region.name)
        val whereSql = buildWhereSql(request, params)
        params.add(request.pageSize)
        params.add(request.page * request.pageSize)
        val loc = request.localeColumnSuffix
        val sql =
            """
            $withSql
            SELECT
                d.item_id,
                COALESCE(d.item_name_$loc, d.item_name_en_gb, d.item_name_en_us) AS item_name,
                d.item_media_url,
                d.quality_id,
                d.quality_type,
                COALESCE(d.quality_name_$loc, d.quality_name_en_gb, d.quality_name_en_us) AS quality_name,
                d.item_class_id,
                COALESCE(d.item_class_name_$loc, d.item_class_name_en_gb, d.item_class_name_en_us) AS item_class_name,
                d.item_subclass_id,
                COALESCE(d.item_subclass_name_$loc, d.item_subclass_name_en_gb, d.item_subclass_name_en_us) AS item_subclass_name,
                d.recipe_id,
                d.recipe_rank,
                COALESCE(d.recipe_name_$loc, d.recipe_name_en_gb, d.recipe_name_en_us) AS recipe_name,
                d.recipe_media_url,
                page.selected_bonus_key,
                page.selected_modifier_key,
                page.selected_pet_species_id,
                page.selected_price,
                page.selected_p25_price,
                page.selected_p75_price,
                page.selected_quantity,
                page.commodity_price,
                page.commodity_p25_price,
                page.commodity_p75_price,
                page.commodity_quantity,
                page.sale_rate,
                page.sold_per_day,
                page.total_items
            FROM (
                SELECT
                    p.*,
                    tsm.sale_rate,
                    tsm.sold_per_day,
                    ${sortValueSql(request)} AS sort_value,
                    ${sortNameSql(request)} AS sort_name,
                    COUNT(*) OVER () AS total_items
                FROM market_prices p
                    $itemSource
                    LEFT JOIN tsm_region_metric tsm
                        ON tsm.region = ?
                        AND tsm.subject_type = 'ITEM'
                        AND tsm.subject_id = p.item_id
                $whereSql
                ${buildOrderBySql(request, "p.item_id")}
                LIMIT ? OFFSET ?
            ) page
                STRAIGHT_JOIN v_auction_market_item_details d ON d.item_id = page.item_id
            ${buildOrderBySql(request, "page.item_id", "page.sort_value", "page.sort_name")}
            """.trimIndent()
        return Pair(sql, params.toTypedArray())
    }

    private fun buildCountSql(
        request: AuctionSearchRequest,
        params: MutableList<Any?>,
    ): String {
        val withSql = buildCurrentAuctionSnapshotCtes(request, params)
        val itemSource = itemSourceSql(request)
        params.add(request.region.name)
        val whereSql = buildWhereSql(request, params)
        return """
            $withSql
            SELECT COUNT(*)
            FROM market_prices p
                $itemSource
                LEFT JOIN tsm_region_metric tsm
                    ON tsm.region = ?
                    AND tsm.subject_type = 'ITEM'
                    AND tsm.subject_id = p.item_id
            $whereSql
            """.trimIndent()
    }

    /** The details view is only joined for rows that need it to filter or sort; otherwise just the item (and its name). */
    private fun usesDetailsView(request: AuctionSearchRequest): Boolean =
        !request.query.isNullOrBlank() || request.sortBy in setOf("quality", "itemClass", "itemSubclass")

    /** Unfiltered metric sorts break ties on item id only, so they skip the item name lookup for every row. */
    private fun usesNameTieBreak(request: AuctionSearchRequest): Boolean =
        request.sortBy !in AUCTION_MARKET_LIGHTWEIGHT_SORT_COLUMNS || hasFilters(request)

    private fun hasFilters(request: AuctionSearchRequest): Boolean =
        !request.query.isNullOrBlank() ||
            pushesItemDimensionFiltersIntoAuctionSnapshot(request) ||
            listOf(
                request.minPrice,
                request.maxPrice,
                request.minQuantity,
                request.maxQuantity,
                request.minSaleRatePercent,
                request.maxSaleRatePercent,
                request.minSoldPerDay,
                request.maxSoldPerDay,
            ).any { it != null }

    private fun itemSourceSql(request: AuctionSearchRequest): String =
        when {
            usesDetailsView(request) -> "STRAIGHT_JOIN v_auction_market_item_details d ON d.item_id = p.item_id"
            usesNameTieBreak(request) ->
                "STRAIGHT_JOIN v_item i ON i.id = p.item_id\n                    LEFT JOIN locale i_l ON i_l.id = i.name_id"
            else -> "STRAIGHT_JOIN v_item i ON i.id = p.item_id"
        }

    private fun sortNameSql(request: AuctionSearchRequest): String {
        val loc = request.localeColumnSuffix
        return when {
            usesDetailsView(request) -> "COALESCE(d.item_name_$loc, d.item_name_en_gb, d.item_name_en_us)"
            usesNameTieBreak(request) -> "COALESCE(i_l.$loc, i_l.en_gb, i_l.en_us)"
            else -> "NULL"
        }
    }

    private fun sortValueSql(request: AuctionSearchRequest): String {
        val loc = request.localeColumnSuffix
        return when (request.sortBy) {
            "selectedPrice", "commodityPrice" -> "COALESCE(p.selected_price, p.commodity_price)"
            "selectedQuantity", "commodityQuantity" -> "COALESCE(p.selected_quantity, p.commodity_quantity)"
            "saleRate" -> "tsm.sale_rate"
            "soldPerDay" -> "tsm.sold_per_day"
            "quality" -> "COALESCE(d.quality_name_$loc, d.quality_name_en_gb, d.quality_name_en_us)"
            "itemClass" -> "COALESCE(d.item_class_name_$loc, d.item_class_name_en_gb, d.item_class_name_en_us)"
            "itemSubclass" -> "COALESCE(d.item_subclass_name_$loc, d.item_subclass_name_en_gb, d.item_subclass_name_en_us)"
            else -> sortNameSql(request)
        }
    }

    /**
     * Single listing price/quantity for sort: realm and commodity are mutually exclusive in the UI,
     * so `selectedPrice` / `commodityPrice` (and quantity counterparts) share the same ORDER BY.
     * Metric sorts put nulls last (MariaDB has no NULLS LAST).
     */
    private fun buildOrderBySql(
        request: AuctionSearchRequest,
        itemIdColumn: String,
        sortValueColumn: String = sortValueSql(request),
        sortNameColumn: String = sortNameSql(request),
    ): String {
        val dir = if (request.sortDirection.equals("desc", ignoreCase = true)) "DESC" else "ASC"
        val primary =
            if (request.sortBy in AUCTION_MARKET_LIGHTWEIGHT_SORT_COLUMNS) {
                "($sortValueColumn IS NULL) ASC, $sortValueColumn $dir"
            } else {
                "$sortValueColumn $dir"
            }
        val nameTieBreak = if (usesNameTieBreak(request)) "$sortNameColumn ASC, " else ""
        return "ORDER BY $primary, $nameTieBreak$itemIdColumn ASC"
    }


    /**
     * When set, quality/class/subclass/recipe filters are applied inside this subquery (via `v_item`)
     * so ranking touches fewer auction rows before joining the heavy view.
     */
    private fun pushesItemDimensionFiltersIntoAuctionSnapshot(request: AuctionSearchRequest): Boolean =
        request.qualityIds.isNotEmpty() ||
            request.itemClassIds.isNotEmpty() ||
            request.itemSubclassIds.isNotEmpty() ||
            request.expansionIds.isNotEmpty() ||
            request.recipeOnly == true

    /**
     * Cheapest current listing per item for the selected realm and the commodity house, one row per item.
     * Each listing is packed into one string that sorts like `price, bonus_key, modifier_key, pet_species_id`,
     * so a single `MIN` per item picks the same row as a `ROW_NUMBER()` ranking would, without the window sort.
     * The covering index `idx_auction_current_listing` serves the scan without row lookups.
     */
    private fun buildCurrentAuctionSnapshotCtes(
        request: AuctionSearchRequest,
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
            auction_listing AS (
                SELECT
                    a.item_id,
                    lh.side,
                    CONCAT_WS(
                        CHAR(1),
                        LPAD(a.buyout, 20, '0'),
                        a.bonus_key,
                        a.modifier_key,
                        LPAD(COALESCE(a.pet_species_id, -1) + 1, 11, '0'),
                        COALESCE(a.p25, ''),
                        COALESCE(a.p75, ''),
                        a.quantity
                    ) AS listing
                FROM auction a
                    INNER JOIN latest_history lh
                        ON lh.connected_realm_id = a.connected_realm_id
                        AND lh.update_history_id = a.update_history_id
                $itemJoin
                WHERE a.buyout IS NOT NULL
                $itemFilterSql
            ),
            cheapest_listing AS (
                SELECT
                    item_id,
                    MIN(CASE WHEN side = 'sel' THEN listing END) AS sel,
                    MIN(CASE WHEN side = 'com' THEN listing END) AS com
                FROM auction_listing
                GROUP BY item_id
            ),
            market_prices AS (
                SELECT
                    item_id,
                    ${listingField("COALESCE(sel, com)", 2)} AS selected_bonus_key,
                    ${listingField("COALESCE(sel, com)", 3)} AS selected_modifier_key,
                    CAST(${listingField("COALESCE(sel, com)", 4)} AS SIGNED) - 1 AS selected_pet_species_id,
                    CAST(${listingField("sel", 1)} AS SIGNED) AS selected_price,
                    CAST(NULLIF(${listingField("sel", 5)}, '') AS SIGNED) AS selected_p25_price,
                    CAST(NULLIF(${listingField("sel", 6)}, '') AS SIGNED) AS selected_p75_price,
                    CAST(${listingField("sel", 7)} AS SIGNED) AS selected_quantity,
                    CAST(${listingField("com", 1)} AS SIGNED) AS commodity_price,
                    CAST(NULLIF(${listingField("com", 5)}, '') AS SIGNED) AS commodity_p25_price,
                    CAST(NULLIF(${listingField("com", 6)}, '') AS SIGNED) AS commodity_p75_price,
                    CAST(${listingField("com", 7)} AS SIGNED) AS commodity_quantity
                FROM cheapest_listing
            )
            """.trimIndent()
    }

    /** Field [position] (1-based) of a listing packed by `auction_listing`. */
    private fun listingField(
        listing: String,
        position: Int,
    ): String = "SUBSTRING_INDEX(SUBSTRING_INDEX($listing, CHAR(1), $position), CHAR(1), -1)"

    /** IDs match `d.item_subclass_id` (subclass_id), not `item.item_subclass_id` (internal_id). */
    private fun buildItemDimensionFilterSql(request: AuctionSearchRequest): String {
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
            parts.add("EXISTS (SELECT 1 FROM v_recipe_crafted_output r WHERE r.crafted_item_id = i.id)")
        }
        return if (parts.isEmpty()) "" else "  AND " + parts.joinToString(" AND ")
    }

    private fun appendItemDimensionFilterParams(
        params: MutableList<Any?>,
        request: AuctionSearchRequest,
    ) {
        if (request.qualityIds.isNotEmpty()) params.addAll(request.qualityIds)
        if (request.itemClassIds.isNotEmpty()) params.addAll(request.itemClassIds)
        if (request.itemSubclassIds.isNotEmpty()) params.addAll(request.itemSubclassIds)
        if (request.expansionIds.isNotEmpty()) params.addAll(request.expansionIds)
    }

    private fun buildWhereSql(
        request: AuctionSearchRequest,
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
        request.minSaleRatePercent?.let {
            predicates.add("tsm.sale_rate IS NOT NULL AND tsm.sale_rate >= ?")
            params.add(it / 100.0)
        }
        request.maxSaleRatePercent?.let {
            predicates.add("tsm.sale_rate IS NOT NULL AND tsm.sale_rate <= ?")
            params.add(it / 100.0)
        }
        request.minSoldPerDay?.let {
            predicates.add("tsm.sold_per_day IS NOT NULL AND tsm.sold_per_day >= ?")
            params.add(it)
        }
        request.maxSoldPerDay?.let {
            predicates.add("tsm.sold_per_day IS NOT NULL AND tsm.sold_per_day <= ?")
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

    private fun correlationId(): String = MDC.get("correlationId") ?: "-"

    /** For integration tests: `EXPLAIN` / `EXPLAIN ANALYZE` against real MariaDB. */
    internal fun buildAuctionSearchPagedSqlForExplain(request: AuctionSearchRequest): Pair<String, Array<Any?>> =
        buildPagedSql(request)

    internal fun buildQualityOptionsSqlForExplain(request: AuctionSearchRequest): Pair<String, Array<Any?>> {
        val qualityOrderSql = ItemQualityOrder.sqlOrderByCase("iq.type")
        return Pair(
            """
            SELECT
                CAST(iq.internal_id AS CHAR) AS id,
                COALESCE(l.${request.localeColumnSuffix}, l.en_gb, l.en_us, iq.type, CAST(iq.internal_id AS CHAR)) AS label,
                NULL AS parent_id,
                iq.type AS quality_type
            FROM item_quality iq
                LEFT JOIN locale l ON l.id = iq.name_id
            WHERE iq.type IS NOT NULL
            ORDER BY $qualityOrderSql, iq.internal_id ASC
            """.trimIndent(),
            emptyArray(),
        )
    }

    internal fun buildItemClassOptionsSqlForExplain(request: AuctionSearchRequest): Pair<String, Array<Any?>> =
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

    internal fun buildItemSubclassOptionsSqlForExplain(request: AuctionSearchRequest): Pair<String, Array<Any?>> =
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
