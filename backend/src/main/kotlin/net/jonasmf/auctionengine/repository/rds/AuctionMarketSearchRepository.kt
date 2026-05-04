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
    val communityConnectedRealmId: Int,
    val communityDate: LocalDate,
    val communityHour: Int,
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
    val selectedPrice: Long?,
    val selectedQuantity: Long?,
    val communityPrice: Long?,
    val communityQuantity: Long?,
)

data class AuctionMarketFilterOptionRow(
    val id: String,
    val label: String,
    val parentId: String? = null,
)

data class AuctionMarketRange(
    val minPrice: Long?,
    val maxPrice: Long?,
    val minQuantity: Long?,
    val maxQuantity: Long?,
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
            "communityPrice" to "community_price",
            "selectedQuantity" to "selected_quantity",
            "communityQuantity" to "community_quantity",
        )

    fun search(request: AuctionMarketSearchRequest): AuctionMarketSearchResult {
        val totalStartNanos = System.nanoTime()
        val params = ArrayList<Any?>()
        val fromSql = buildFromSql(request, params)
        val whereSql = buildWhereSql(request, params)
        val sortColumn = sortColumns[request.sortBy] ?: sortColumns.getValue("itemName")
        val sortDirection = if (request.sortDirection.equals("desc", ignoreCase = true)) "DESC" else "ASC"
        val offset = request.page * request.pageSize
        params.add(request.pageSize)
        params.add(offset)

        val sql = buildSearchPagedSql(request, fromSql, whereSql, sortColumn, sortDirection)
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
            "Auction market search repository completed in {}ms (requestId={} query={}ms selectedRealm={} selectedDate={} selectedHour={} communityRealm={} communityDate={} communityHour={} totalItems={} returnedRows={})",
            elapsedMs(totalStartNanos),
            requestId(),
            queryMs,
            request.selectedConnectedRealmId,
            request.selectedDate,
            request.selectedHour,
            request.communityConnectedRealmId,
            request.communityDate,
            request.communityHour,
            totalItems,
            rows.size,
        )

        return AuctionMarketSearchResult(rows = rows, totalItems = totalItems)
    }

    fun qualityOptions(request: AuctionMarketSearchRequest): List<AuctionMarketFilterOptionRow> =
        optionQuery(
            request = request,
            idExpression = "d.quality_id",
            labelExpression =
                "COALESCE(d.quality_name_${request.localeColumnSuffix}, d.quality_name_en_gb, d.quality_name_en_us)",
            parentExpression = null,
            predicate = "d.quality_id IS NOT NULL",
        )

    fun itemClassOptions(request: AuctionMarketSearchRequest): List<AuctionMarketFilterOptionRow> =
        optionQuery(
            request = request,
            idExpression = "d.item_class_id",
            labelExpression =
                "COALESCE(d.item_class_name_${request.localeColumnSuffix}, d.item_class_name_en_gb, d.item_class_name_en_us)",
            parentExpression = null,
            predicate = "d.item_class_id IS NOT NULL",
        )

    fun itemSubclassOptions(request: AuctionMarketSearchRequest): List<AuctionMarketFilterOptionRow> =
        optionQuery(
            request = request,
            idExpression = "d.item_subclass_id",
            labelExpression =
                "COALESCE(d.item_subclass_name_${request.localeColumnSuffix}, d.item_subclass_name_en_gb, d.item_subclass_name_en_us)",
            parentExpression = "d.item_class_id",
            predicate = "d.item_subclass_id IS NOT NULL",
        )

    fun priceAndQuantityRange(request: AuctionMarketSearchRequest): AuctionMarketRange {
        val params = ArrayList<Any?>()
        val fromSql = buildFromSql(request, params)
        return jdbcTemplate.queryForObject(
            """
            SELECT
                MIN(s.price) AS min_price,
                MAX(s.price) AS max_price,
                MIN(s.quantity) AS min_quantity,
                MAX(s.quantity) AS max_quantity
            $fromSql
            """.trimIndent(),
            { rs, _ ->
                AuctionMarketRange(
                    minPrice = rs.getNullableLong("min_price"),
                    maxPrice = rs.getNullableLong("max_price"),
                    minQuantity = rs.getNullableLong("min_quantity"),
                    maxQuantity = rs.getNullableLong("max_quantity"),
                )
            },
            *params.toTypedArray(),
        ) ?: AuctionMarketRange(null, null, null, null)
    }

    private fun optionQuery(
        request: AuctionMarketSearchRequest,
        idExpression: String,
        labelExpression: String,
        parentExpression: String?,
        predicate: String,
    ): List<AuctionMarketFilterOptionRow> {
        val params = ArrayList<Any?>()
        val fromSql = buildFromSql(request, params)
        val parentSelect = parentExpression?.let { ", CAST($it AS CHAR) AS parent_id" } ?: ", NULL AS parent_id"
        val parentGroup = parentExpression?.let { ", $it" } ?: ""

        return jdbcTemplate.query(
            """
            SELECT CAST($idExpression AS CHAR) AS id, $labelExpression AS label $parentSelect
            $fromSql
            WHERE $predicate
            GROUP BY $idExpression, $labelExpression $parentGroup
            ORDER BY label ASC
            """.trimIndent(),
            { rs, _ ->
                AuctionMarketFilterOptionRow(
                    id = rs.getString("id"),
                    label = rs.getString("label") ?: rs.getString("id"),
                    parentId = rs.getString("parent_id"),
                )
            },
            *params.toTypedArray(),
        )
    }

    private fun buildSearchPagedSql(
        request: AuctionMarketSearchRequest,
        fromSql: String,
        whereSql: String,
        sortColumn: String,
        sortDirection: String,
    ): String =
        """
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
            wrapped.selected_price,
            wrapped.selected_quantity,
            wrapped.community_price,
            wrapped.community_quantity,
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
                s.price AS selected_price,
                s.quantity AS selected_quantity,
                c.price AS community_price,
                c.quantity AS community_quantity,
                COUNT(*) OVER () AS total_items
            $fromSql
            $whereSql
        ) wrapped
        ORDER BY wrapped.$sortColumn $sortDirection, wrapped.item_name ASC, wrapped.item_id ASC
        LIMIT ? OFFSET ?
        """.trimIndent()

    private fun buildFromSql(
        request: AuctionMarketSearchRequest,
        params: MutableList<Any?>,
    ): String {
        params.add(request.selectedConnectedRealmId)
        params.add(java.sql.Date.valueOf(request.selectedDate))
        params.add(request.communityConnectedRealmId)
        params.add(java.sql.Date.valueOf(request.communityDate))

        return """
            FROM v_auction_market_item_details d
                     JOIN (${buildHourlyAggregateSql(request.selectedHour)}) s ON s.item_id = d.item_id
                     LEFT JOIN (${buildHourlyAggregateSql(request.communityHour)}) c ON c.item_id = d.item_id
            """.trimIndent()
    }

    private fun buildHourlyAggregateSql(hour: Int): String {
        val hourSuffix = hourColumnSuffix(hour)
        val priceColumn = "price$hourSuffix"
        val quantityColumn = "quantity$hourSuffix"

        return """
            SELECT item_id, MIN($priceColumn) AS price, SUM($quantityColumn) AS quantity
            FROM auction_stats_hourly
            WHERE connected_realm_id = ?
              AND date = ?
              AND $priceColumn IS NOT NULL
            GROUP BY item_id
            """.trimIndent()
    }

    private fun hourColumnSuffix(hour: Int): String {
        require(hour in 0..23) { "Hour must be between 0 and 23: $hour" }
        return hour.toString().padStart(2, '0')
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
        addInPredicate("d.quality_id", request.qualityIds, predicates, params)
        addInPredicate("d.item_class_id", request.itemClassIds, predicates, params)
        addInPredicate("d.item_subclass_id", request.itemSubclassIds, predicates, params)
        if (request.recipeOnly == true) {
            predicates.add("d.recipe_id IS NOT NULL")
        }
        request.minPrice?.let {
            predicates.add("s.price >= ?")
            params.add(it)
        }
        request.maxPrice?.let {
            predicates.add("s.price <= ?")
            params.add(it)
        }
        request.minQuantity?.let {
            predicates.add("s.quantity >= ?")
            params.add(it)
        }
        request.maxQuantity?.let {
            predicates.add("s.quantity <= ?")
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
                selectedPrice = rs.getNullableLong("selected_price"),
                selectedQuantity = rs.getNullableLong("selected_quantity"),
                communityPrice = rs.getNullableLong("community_price"),
                communityQuantity = rs.getNullableLong("community_quantity"),
            )
        }

    private val rowMapperWithTotal =
        RowMapper { rs: ResultSet, rowNum: Int ->
            val totalItems = rs.getLong("total_items")
            val row = requireNotNull(rowMapper.mapRow(rs, rowNum)) { "market search row expected" }
            row to totalItems
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
        val fromSql = buildFromSql(request, params)
        val whereSql = buildWhereSql(request, params)
        val sortColumn = sortColumns[request.sortBy] ?: sortColumns.getValue("itemName")
        val sortDirection = if (request.sortDirection.equals("desc", ignoreCase = true)) "DESC" else "ASC"
        val offset = request.page * request.pageSize
        params.add(request.pageSize)
        params.add(offset)
        return Pair(buildSearchPagedSql(request, fromSql, whereSql, sortColumn, sortDirection), params.toTypedArray())
    }

    internal fun buildPriceAndQuantityRangeSqlForExplain(request: AuctionMarketSearchRequest): Pair<String, Array<Any?>> {
        val params = ArrayList<Any?>()
        val fromSql = buildFromSql(request, params)
        val sql =
            """
            SELECT
                MIN(s.price) AS min_price,
                MAX(s.price) AS max_price,
                MIN(s.quantity) AS min_quantity,
                MAX(s.quantity) AS max_quantity
            $fromSql
            """.trimIndent()
        return Pair(sql, params.toTypedArray())
    }
}
