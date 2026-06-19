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
    val selectedQuantity: Long?,
    val commodityPrice: Long?,
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
            wrapped.selected_quantity,
            wrapped.commodity_price,
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
                COALESCE(s.bonus_key, c.bonus_key) AS selected_bonus_key,
                COALESCE(s.modifier_key, c.modifier_key) AS selected_modifier_key,
                COALESCE(s.pet_species_id, c.pet_species_id) AS selected_pet_species_id,
                s.price AS selected_price,
                s.quantity AS selected_quantity,
                c.price AS commodity_price,
                c.quantity AS commodity_quantity,
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
     * One ranked current auction snapshot per side (`sel`, `com`) as CTEs, then `u` = union
     * of item ids so commodity-only listings appear. Join `d` from `u` (not only from realm auctions).
     */
    private fun buildWithAndFromSql(
        request: AuctionMarketSearchRequest,
        params: MutableList<Any?>,
    ): Pair<String, String> {
        val selCtes =
            buildCurrentAuctionCtes(
                "sel",
                request.selectedConnectedRealmId,
                request,
                params,
            )
        val comCtes =
            buildCurrentAuctionCtes(
                "com",
                request.commodityConnectedRealmId,
                request,
                params,
            )
        val withSql =
            """
            WITH
            $selCtes,
            $comCtes,
            u AS (
                SELECT item_id FROM sel
                UNION
                SELECT item_id FROM com
            )
            """.trimIndent()
        val fromSql =
            """
            FROM u
                     STRAIGHT_JOIN v_auction_market_item_details d ON d.item_id = u.item_id
                     LEFT JOIN sel s ON s.item_id = u.item_id
                     LEFT JOIN com c ON c.item_id = u.item_id
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
            request.recipeOnly == true

    private fun buildCurrentAuctionCtes(
        ctePrefix: String,
        connectedRealmId: Int,
        request: AuctionMarketSearchRequest,
        params: MutableList<Any?>,
    ): String {
        val useItemJoin = pushesItemDimensionFiltersIntoAuctionSnapshot(request)
        val itemJoin =
            if (useItemJoin) {
                "INNER JOIN item i ON i.id = a.item_id\n                "
            } else {
                ""
            }
        val itemFilterSql = if (useItemJoin) buildItemDimensionFilterSql(request) else ""
        params.add(connectedRealmId)
        params.add(connectedRealmId)
        if (useItemJoin) {
            appendItemDimensionFilterParams(params, request)
        }
        return """
            ${ctePrefix}_latest_history AS (
                SELECT MAX(id) AS update_history_id
                FROM connected_realm_update_history
                WHERE connected_realm_id = ?
            ),
            ${ctePrefix}_base AS (
                SELECT
                    a.item_id,
                    a.bonus_key,
                    a.modifier_key,
                    COALESCE(a.pet_species_id, -1) AS pet_species_id,
                    a.buyout AS price,
                    a.quantity AS quantity
                FROM auction a
                INNER JOIN ${ctePrefix}_latest_history lh ON lh.update_history_id = a.update_history_id
                $itemJoin
                WHERE a.connected_realm_id = ?
                  AND a.buyout IS NOT NULL
                $itemFilterSql
            ),
            ${ctePrefix}_ranked AS (
                SELECT
                    item_id,
                    bonus_key,
                    modifier_key,
                    pet_species_id,
                    price,
                    quantity,
                    ROW_NUMBER() OVER (
                        PARTITION BY item_id
                        ORDER BY price ASC, bonus_key, modifier_key, pet_species_id
                    ) AS rn
                FROM ${ctePrefix}_base
            ),
            $ctePrefix AS (
                SELECT item_id, bonus_key, modifier_key, pet_species_id, price, quantity
                FROM ${ctePrefix}_ranked
                WHERE rn = 1
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
            if (request.recipeOnly == true) {
                predicates.add("d.recipe_id IS NOT NULL")
            }
        }
        request.minPrice?.let {
            predicates.add("COALESCE(s.price, c.price) >= ?")
            params.add(it)
        }
        request.maxPrice?.let {
            predicates.add("COALESCE(s.price, c.price) <= ?")
            params.add(it)
        }
        request.minQuantity?.let {
            predicates.add("COALESCE(s.quantity, c.quantity) >= ?")
            params.add(it)
        }
        request.maxQuantity?.let {
            predicates.add("COALESCE(s.quantity, c.quantity) <= ?")
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
                selectedQuantity = rs.getNullableLong("selected_quantity"),
                commodityPrice = rs.getNullableLong("commodity_price"),
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
