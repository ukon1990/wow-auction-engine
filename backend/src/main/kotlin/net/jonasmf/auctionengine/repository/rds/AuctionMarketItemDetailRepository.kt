package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.time.LocalDate

@Repository
class AuctionMarketItemDetailRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun loadItemHeader(
        itemId: Int,
        localeColumnSuffix: String,
    ): AuctionMarketItemHeaderRow? =
        jdbcTemplate
            .query(
                """
                SELECT
                    d.item_id,
                    COALESCE(d.item_name_$localeColumnSuffix, d.item_name_en_gb, d.item_name_en_us) AS item_name,
                    d.item_media_url,
                    d.quality_id,
                    d.quality_type,
                    COALESCE(d.quality_name_$localeColumnSuffix, d.quality_name_en_gb, d.quality_name_en_us) AS quality_name,
                    d.item_class_id,
                    COALESCE(d.item_class_name_$localeColumnSuffix, d.item_class_name_en_gb, d.item_class_name_en_us) AS item_class_name,
                    d.item_subclass_id,
                    COALESCE(d.item_subclass_name_$localeColumnSuffix, d.item_subclass_name_en_gb, d.item_subclass_name_en_us) AS item_subclass_name,
                    d.recipe_id,
                    d.recipe_rank,
                    COALESCE(d.recipe_name_$localeColumnSuffix, d.recipe_name_en_gb, d.recipe_name_en_us) AS recipe_name,
                    d.recipe_media_url
                FROM v_auction_market_item_details d
                WHERE d.item_id = ?
                LIMIT 1
                """.trimIndent(),
                AuctionMarketItemDetailRowMappers.headerRowMapper,
                itemId,
            ).firstOrNull()

    fun loadSnapshotPriceQuantity(
        connectedRealmId: Int,
        itemId: Int,
        statDate: LocalDate,
        hourOfDay: Int,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): Pair<Long?, Long?> {
        val whereVar =
            if (variant) {
                "AND bonus_key <=> ? AND modifier_key <=> ? AND pet_species_id = ?"
            } else {
                ""
            }
        val sql =
            """
            SELECT MIN(price) AS price, SUM(quantity) AS qty
            FROM v_auction_house_prices
            WHERE connected_realm_id = ?
              AND item_id = ?
              AND date = ?
              AND hour_of_day = ?
              $whereVar
            """.trimIndent()
        val params: Array<Any?> =
            if (variant) {
                arrayOf(
                    connectedRealmId,
                    itemId,
                    Date.valueOf(statDate),
                    hourOfDay,
                    bonusKey,
                    modifierKey,
                    petSpeciesId,
                )
            } else {
                arrayOf(
                    connectedRealmId,
                    itemId,
                    Date.valueOf(statDate),
                    hourOfDay,
                )
            }
        return jdbcTemplate
            .query(sql, AuctionMarketItemDetailRowMappers.snapshotRowMapper, *params)
            .firstOrNull() ?: (null to null)
    }

    fun loadCurrentListings(
        connectedRealmId: Int,
        itemId: Int,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): List<AuctionMarketItemCurrentListingRow> {
        val (sql, params) =
            buildCurrentListingsSqlAndArgs(
                connectedRealmId,
                itemId,
                variant,
                bonusKey,
                modifierKey,
                petSpeciesId,
            )
        return jdbcTemplate.query(sql, AuctionMarketItemDetailRowMappers.currentListingRowMapper, *params)
    }

    fun loadDailySeries(
        connectedRealmId: Int,
        itemId: Int,
        fromDate: LocalDate,
        toDate: LocalDate,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): List<AuctionMarketItemDetailDailyRow> {
        val (sql, params) =
            buildDailySqlAndArgs(
                connectedRealmId,
                itemId,
                fromDate,
                toDate,
                variant,
                bonusKey,
                modifierKey,
                petSpeciesId,
            )
        return jdbcTemplate.query(sql, AuctionMarketItemDetailRowMappers.dailyRowMapper, *params)
    }

    fun loadHourlySeries(
        connectedRealmId: Int,
        itemId: Int,
        fromDate: LocalDate,
        toDate: LocalDate,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): List<AuctionMarketItemDetailHourlyRow> {
        val (sql, params) =
            buildHourlySqlAndArgs(
                connectedRealmId,
                itemId,
                fromDate,
                toDate,
                variant,
                bonusKey,
                modifierKey,
                petSpeciesId,
            )
        return jdbcTemplate.query(sql, AuctionMarketItemDetailRowMappers.hourlyRowMapper, *params)
    }

    fun loadQuantityPie(
        connectedRealmId: Int,
        itemId: Int,
        statDate: LocalDate,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): List<AuctionMarketItemDetailPieRow> {
        val (sql, params) =
            buildPieSqlAndArgs(
                connectedRealmId,
                itemId,
                statDate,
                variant,
                bonusKey,
                modifierKey,
                petSpeciesId,
            )
        return jdbcTemplate.query(sql, AuctionMarketItemDetailRowMappers.pieRowMapper, *params)
    }

    fun loadCraftings(
        connectedRealmId: Int,
        commodityConnectedRealmId: Int,
        itemId: Int,
        statDate: LocalDate,
        commodityStatDate: LocalDate,
        hourOfDay: Int,
        commodityHourOfDay: Int,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
        preferredRecipeId: Int?,
        localeColumnSuffix: String,
    ): List<AuctionMarketItemCraftingRow> {
        val (sql, params) =
            buildCraftingSqlAndArgs(
                connectedRealmId,
                commodityConnectedRealmId,
                itemId,
                statDate,
                commodityStatDate,
                hourOfDay,
                commodityHourOfDay,
                variant,
                bonusKey,
                modifierKey,
                petSpeciesId,
                preferredRecipeId,
                localeColumnSuffix,
            )
        return jdbcTemplate.query(sql, AuctionMarketItemDetailRowMappers.craftingRowMapper, *params)
    }

    fun loadCraftingReagents(
        connectedRealmId: Int,
        commodityConnectedRealmId: Int,
        recipeIds: List<Int>,
        statDate: LocalDate,
        commodityStatDate: LocalDate,
        hourOfDay: Int,
        commodityHourOfDay: Int,
        localeColumnSuffix: String,
    ): List<AuctionMarketItemCraftingReagentRow> {
        if (recipeIds.isEmpty()) return emptyList()
        val placeholders = recipeIds.joinToString(",") { "?" }
        val hourSuffix = hourColumnSuffix(hourOfDay)
        val commodityHourSuffix = hourColumnSuffix(commodityHourOfDay)
        val sql =
            """
            WITH reagent_sel_base AS (
                SELECT ash.item_id, ash.price$hourSuffix AS price, ash.bonus_key, ash.modifier_key, ash.pet_species_id
                FROM auction_stats_hourly ash
                WHERE ash.connected_realm_id = ?
                  AND ash.date = ?
                  AND ash.price$hourSuffix IS NOT NULL
                  AND ash.item_id IN (SELECT DISTINCT rr.item_id FROM v_recipe_reagent rr WHERE rr.recipe_id IN ($placeholders))
            ),
            reagent_sel_ranked AS (
                SELECT item_id, price,
                       ROW_NUMBER() OVER (PARTITION BY item_id ORDER BY price ASC, bonus_key, modifier_key, pet_species_id) AS rn
                FROM reagent_sel_base
            ),
            reagent_sel AS (
                SELECT item_id, price FROM reagent_sel_ranked WHERE rn = 1
            ),
            reagent_com_base AS (
                SELECT ash.item_id, ash.price$commodityHourSuffix AS price, ash.bonus_key, ash.modifier_key, ash.pet_species_id
                FROM auction_stats_hourly ash
                WHERE ash.connected_realm_id = ?
                  AND ash.date = ?
                  AND ash.price$commodityHourSuffix IS NOT NULL
                  AND ash.item_id IN (SELECT DISTINCT rr.item_id FROM v_recipe_reagent rr WHERE rr.recipe_id IN ($placeholders))
            ),
            reagent_com_ranked AS (
                SELECT item_id, price,
                       ROW_NUMBER() OVER (PARTITION BY item_id ORDER BY price ASC, bonus_key, modifier_key, pet_species_id) AS rn
                FROM reagent_com_base
            ),
            reagent_com AS (
                SELECT item_id, price FROM reagent_com_ranked WHERE rn = 1
            ),
            reagent_price AS (
                SELECT items.item_id, COALESCE(rs.price, rc.price) AS price
                FROM (SELECT DISTINCT item_id FROM v_recipe_reagent WHERE recipe_id IN ($placeholders)) items
                LEFT JOIN reagent_sel rs ON rs.item_id = items.item_id
                LEFT JOIN reagent_com rc ON rc.item_id = items.item_id
            )
            SELECT
                rr.recipe_id,
                rr.item_id,
                COALESCE(i_l.$localeColumnSuffix, i_l.en_gb, i_l.en_us, CONCAT('Item ', rr.item_id)) AS name,
                i.media_url AS media_url,
                rr.quantity,
                rp.price AS unit_price,
                CASE WHEN rp.price IS NULL THEN NULL ELSE rp.price * rr.quantity END AS line_total
            FROM v_recipe_reagent rr
            LEFT JOIN reagent_price rp ON rp.item_id = rr.item_id
            LEFT JOIN v_item i ON i.id = rr.item_id
            LEFT JOIN locale i_l ON i_l.id = i.name_id
            WHERE rr.recipe_id IN ($placeholders)
            ORDER BY rr.recipe_id, name, rr.item_id
            """.trimIndent()
        val params =
            arrayOf<Any?>(
                connectedRealmId,
                Date.valueOf(statDate),
                *recipeIds.toTypedArray(),
                commodityConnectedRealmId,
                Date.valueOf(commodityStatDate),
                *recipeIds.toTypedArray(),
                *recipeIds.toTypedArray(),
                *recipeIds.toTypedArray(),
            )
        return jdbcTemplate.query(sql, AuctionMarketItemDetailRowMappers.craftingReagentRowMapper, *params)
    }

    internal fun buildCurrentListingsSqlAndArgs(
        connectedRealmId: Int,
        itemId: Int,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): Pair<String, Array<Any?>> =
        AuctionMarketItemDetailSql.buildCurrentListingsSqlAndArgs(
            connectedRealmId,
            itemId,
            variant,
            bonusKey,
            modifierKey,
            petSpeciesId,
        )

    internal fun buildDailySqlAndArgs(
        connectedRealmId: Int,
        itemId: Int,
        fromDate: LocalDate,
        toDate: LocalDate,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): Pair<String, Array<Any?>> =
        AuctionMarketItemDetailSql.buildDailySqlAndArgs(
            connectedRealmId,
            itemId,
            fromDate,
            toDate,
            variant,
            bonusKey,
            modifierKey,
            petSpeciesId,
        )

    internal fun buildHourlySqlAndArgs(
        connectedRealmId: Int,
        itemId: Int,
        fromDate: LocalDate,
        toDate: LocalDate,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): Pair<String, Array<Any?>> =
        AuctionMarketItemDetailSql.buildHourlySqlAndArgs(
            connectedRealmId,
            itemId,
            fromDate,
            toDate,
            variant,
            bonusKey,
            modifierKey,
            petSpeciesId,
        )

    internal fun buildPieSqlAndArgs(
        connectedRealmId: Int,
        itemId: Int,
        statDate: LocalDate,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): Pair<String, Array<Any?>> =
        AuctionMarketItemDetailSql.buildPieSqlAndArgs(
            connectedRealmId,
            itemId,
            statDate,
            variant,
            bonusKey,
            modifierKey,
            petSpeciesId,
        )

    internal fun buildCraftingSqlAndArgs(
        connectedRealmId: Int,
        commodityConnectedRealmId: Int,
        itemId: Int,
        statDate: LocalDate,
        commodityStatDate: LocalDate,
        hourOfDay: Int,
        commodityHourOfDay: Int,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
        preferredRecipeId: Int?,
        localeColumnSuffix: String,
    ): Pair<String, Array<Any?>> =
        AuctionMarketItemDetailSql.buildCraftingSqlAndArgs(
            connectedRealmId,
            commodityConnectedRealmId,
            itemId,
            statDate,
            commodityStatDate,
            hourOfDay,
            commodityHourOfDay,
            variant,
            bonusKey,
            modifierKey,
            petSpeciesId,
            preferredRecipeId,
            localeColumnSuffix,
        )

    /**
     * Returns true when the given v_recipe exists and produces the given crafted item. Used to enforce
     * the contract on `GET .../auction-market-item-crafting-analytics`, which requires the v_recipe and
     * item to be a valid pair so the analytics can return 404 instead of an empty 200 payload.
     */
    fun recipeProducesItem(
        recipeId: Int,
        itemId: Int,
    ): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM v_recipe_crafted_output WHERE recipe_id = ? AND crafted_item_id = ?)",
            Boolean::class.java,
            recipeId,
            itemId,
        ) == true

    fun loadCraftingAnalyticsDaily(
        connectedRealmId: Int,
        commodityConnectedRealmId: Int,
        itemId: Int,
        recipeId: Int,
        fromDate: LocalDate,
        toDate: LocalDate,
        hourOfDay: Int,
        commodityHourOfDay: Int,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): List<AuctionMarketItemCraftingAnalyticsDailyRow> {
        val hourSuffix = hourColumnSuffix(hourOfDay)
        val commodityHourSuffix = hourColumnSuffix(commodityHourOfDay)
        val outputWhere = if (variant) "AND ash.bonus_key <=> ? AND ash.modifier_key <=> ? AND ash.pet_species_id = ?" else ""
        val sql =
            """
            WITH RECURSIVE date_spine AS (
                SELECT ? AS stat_date
                UNION ALL
                SELECT DATE_ADD(stat_date, INTERVAL 1 DAY) FROM date_spine WHERE stat_date < ?
            ),
            reagent_sel_base AS (
                SELECT ds.stat_date, ash.item_id, ash.price$hourSuffix AS price, ash.bonus_key, ash.modifier_key, ash.pet_species_id
                FROM date_spine ds
                LEFT JOIN auction_stats_hourly ash
                  ON ash.connected_realm_id = ?
                 AND ash.date = ds.stat_date
                 AND ash.price$hourSuffix IS NOT NULL
                 AND ash.item_id IN (SELECT item_id FROM v_recipe_reagent WHERE recipe_id = ?)
            ),
            reagent_sel_ranked AS (
                SELECT stat_date, item_id, price,
                       ROW_NUMBER() OVER (PARTITION BY stat_date, item_id ORDER BY price ASC, bonus_key, modifier_key, pet_species_id) AS rn
                FROM reagent_sel_base WHERE item_id IS NOT NULL
            ),
            reagent_sel AS (
                SELECT stat_date, item_id, price FROM reagent_sel_ranked WHERE rn = 1
            ),
            reagent_com_base AS (
                SELECT ds.stat_date, ash.item_id, ash.price$commodityHourSuffix AS price, ash.bonus_key, ash.modifier_key, ash.pet_species_id
                FROM date_spine ds
                LEFT JOIN auction_stats_hourly ash
                  ON ash.connected_realm_id = ?
                 AND ash.date = ds.stat_date
                 AND ash.price$commodityHourSuffix IS NOT NULL
                 AND ash.item_id IN (SELECT item_id FROM v_recipe_reagent WHERE recipe_id = ?)
            ),
            reagent_com_ranked AS (
                SELECT stat_date, item_id, price,
                       ROW_NUMBER() OVER (PARTITION BY stat_date, item_id ORDER BY price ASC, bonus_key, modifier_key, pet_species_id) AS rn
                FROM reagent_com_base WHERE item_id IS NOT NULL
            ),
            reagent_com AS (
                SELECT stat_date, item_id, price FROM reagent_com_ranked WHERE rn = 1
            ),
            reagent_price AS (
                SELECT ds.stat_date, rr.item_id, COALESCE(rs.price, rc.price) AS price
                FROM date_spine ds
                CROSS JOIN (SELECT DISTINCT item_id FROM v_recipe_reagent WHERE recipe_id = ?) rr
                LEFT JOIN reagent_sel rs ON rs.stat_date = ds.stat_date AND rs.item_id = rr.item_id
                LEFT JOIN reagent_com rc ON rc.stat_date = ds.stat_date AND rc.item_id = rr.item_id
            ),
            reagent_cost AS (
                SELECT ds.stat_date,
                       SUM(CASE WHEN rp.price IS NULL THEN 1 ELSE 0 END) AS missing_reagents,
                       SUM(COALESCE(rp.price, 0) * rr.quantity) AS partial_cost
                FROM date_spine ds
                JOIN v_recipe_reagent rr ON rr.recipe_id = ?
                LEFT JOIN reagent_price rp ON rp.stat_date = ds.stat_date AND rp.item_id = rr.item_id
                GROUP BY ds.stat_date
            ),
            output_sel_base AS (
                SELECT ds.stat_date, ash.price$hourSuffix AS output_unit_price, ash.bonus_key, ash.modifier_key, ash.pet_species_id
                FROM date_spine ds
                LEFT JOIN auction_stats_hourly ash
                  ON ash.connected_realm_id = ?
                 AND ash.date = ds.stat_date
                 AND ash.item_id = ?
                 AND ash.price$hourSuffix IS NOT NULL
                 $outputWhere
            ),
            output_sel_ranked AS (
                SELECT stat_date, output_unit_price,
                       ROW_NUMBER() OVER (PARTITION BY stat_date ORDER BY output_unit_price ASC, bonus_key, modifier_key, pet_species_id) AS rn
                FROM output_sel_base WHERE output_unit_price IS NOT NULL
            ),
            output_sel AS (
                SELECT stat_date, output_unit_price FROM output_sel_ranked WHERE rn = 1
            ),
            output_com_base AS (
                SELECT ds.stat_date, ash.price$commodityHourSuffix AS output_unit_price, ash.bonus_key, ash.modifier_key, ash.pet_species_id
                FROM date_spine ds
                LEFT JOIN auction_stats_hourly ash
                  ON ash.connected_realm_id = ?
                 AND ash.date = ds.stat_date
                 AND ash.item_id = ?
                 AND ash.price$commodityHourSuffix IS NOT NULL
                 $outputWhere
            ),
            output_com_ranked AS (
                SELECT stat_date, output_unit_price,
                       ROW_NUMBER() OVER (PARTITION BY stat_date ORDER BY output_unit_price ASC, bonus_key, modifier_key, pet_species_id) AS rn
                FROM output_com_base WHERE output_unit_price IS NOT NULL
            ),
            output_com AS (
                SELECT stat_date, output_unit_price FROM output_com_ranked WHERE rn = 1
            ),
            output_price AS (
                SELECT ds.stat_date, COALESCE(os.output_unit_price, oc.output_unit_price) AS output_unit_price
                FROM date_spine ds
                LEFT JOIN output_sel os ON os.stat_date = ds.stat_date
                LEFT JOIN output_com oc ON oc.stat_date = ds.stat_date
            ),
            recipe_dim AS (
                SELECT COALESCE(NULLIF(crafted_quantity, 0), 1) AS crafted_quantity FROM v_recipe_crafted_output WHERE recipe_id = ? AND crafted_item_id = ?
            )
            SELECT ds.stat_date,
                   CASE WHEN rc.missing_reagents = 0 THEN rc.partial_cost ELSE NULL END AS reagent_cost,
                   op.output_unit_price,
                   CASE WHEN rc.missing_reagents = 0 AND rc.partial_cost IS NOT NULL AND op.output_unit_price IS NOT NULL
                        THEN op.output_unit_price * rd.crafted_quantity - rc.partial_cost ELSE NULL END AS profit,
                   CASE WHEN rc.missing_reagents = 0 AND rc.partial_cost IS NOT NULL AND rc.partial_cost > 0 AND op.output_unit_price IS NOT NULL
                        THEN 100.0 * (op.output_unit_price * rd.crafted_quantity - rc.partial_cost) / rc.partial_cost ELSE NULL END AS roi_percent
            FROM date_spine ds
            CROSS JOIN recipe_dim rd
            LEFT JOIN reagent_cost rc ON rc.stat_date = ds.stat_date
            LEFT JOIN output_price op ON op.stat_date = ds.stat_date
            ORDER BY ds.stat_date
            """.trimIndent()
        val outputParams: Array<Any?> = if (variant) arrayOf(bonusKey, modifierKey, petSpeciesId) else emptyArray()
        return jdbcTemplate.query(
            sql,
            AuctionMarketItemDetailRowMappers.craftingAnalyticsDailyRowMapper,
            Date.valueOf(fromDate),
            Date.valueOf(toDate),
            connectedRealmId,
            recipeId,
            commodityConnectedRealmId,
            recipeId,
            recipeId,
            recipeId,
            connectedRealmId,
            itemId,
            *outputParams,
            commodityConnectedRealmId,
            itemId,
            *outputParams,
            recipeId,
            itemId,
        )
    }

    /**
     * Loads the (day_of_week, hour_of_day) heatmap of average profit and ROI over the given date window.
     *
     * Implemented as a single SQL query that unpivots the 24 `priceNN`/`quantityNN` columns of
     * `auction_stats_hourly` via a `CASE` expression cross-joined against an inline 0..23 hours table.
     * This replaces a previous loop that issued 24 separate JDBC queries (one per hour), each running a
     * complex CTE pipeline. The new shape executes in a single round-trip and lets MariaDB index-scan
     * `auction_stats_hourly` once per CTE branch instead of 24 times.
     */
    fun loadCraftingAnalyticsHeatmap(
        connectedRealmId: Int,
        commodityConnectedRealmId: Int,
        itemId: Int,
        recipeId: Int,
        fromDate: LocalDate,
        toDate: LocalDate,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): List<AuctionMarketItemCraftingHeatmapRow> {
        val variantWhere =
            if (variant) {
                "AND ash.bonus_key <=> ? AND ash.modifier_key <=> ? AND ash.pet_species_id = ?"
            } else {
                ""
            }
        val priceCase = hourlyPriceCaseExpression()
        val sql =
            """
            WITH RECURSIVE date_spine AS (
                SELECT ? AS stat_date
                UNION ALL
                SELECT DATE_ADD(stat_date, INTERVAL 1 DAY) FROM date_spine WHERE stat_date < ?
            ),
            hours_t AS (${hoursCteSql()}),
            reagent_sel_priced AS (
                SELECT ash.date AS stat_date, h.hour_of_day, ash.item_id,
                       ash.bonus_key, ash.modifier_key, ash.pet_species_id,
                       $priceCase AS price
                FROM hours_t h
                JOIN auction_stats_hourly ash
                  ON ash.connected_realm_id = ?
                 AND ash.date BETWEEN ? AND ?
                 AND ash.item_id IN (SELECT item_id FROM v_recipe_reagent WHERE recipe_id = ?)
                HAVING price IS NOT NULL
            ),
            reagent_sel_ranked AS (
                SELECT stat_date, hour_of_day, item_id, price,
                       ROW_NUMBER() OVER (
                           PARTITION BY stat_date, hour_of_day, item_id
                           ORDER BY price ASC, bonus_key, modifier_key, pet_species_id
                       ) AS rn
                FROM reagent_sel_priced
            ),
            reagent_sel AS (
                SELECT stat_date, hour_of_day, item_id, price FROM reagent_sel_ranked WHERE rn = 1
            ),
            reagent_com_priced AS (
                SELECT ash.date AS stat_date, h.hour_of_day, ash.item_id,
                       ash.bonus_key, ash.modifier_key, ash.pet_species_id,
                       $priceCase AS price
                FROM hours_t h
                JOIN auction_stats_hourly ash
                  ON ash.connected_realm_id = ?
                 AND ash.date BETWEEN ? AND ?
                 AND ash.item_id IN (SELECT item_id FROM v_recipe_reagent WHERE recipe_id = ?)
                HAVING price IS NOT NULL
            ),
            reagent_com_ranked AS (
                SELECT stat_date, hour_of_day, item_id, price,
                       ROW_NUMBER() OVER (
                           PARTITION BY stat_date, hour_of_day, item_id
                           ORDER BY price ASC, bonus_key, modifier_key, pet_species_id
                       ) AS rn
                FROM reagent_com_priced
            ),
            reagent_com AS (
                SELECT stat_date, hour_of_day, item_id, price FROM reagent_com_ranked WHERE rn = 1
            ),
            reagent_price AS (
                SELECT ds.stat_date, h.hour_of_day, rr.item_id,
                       COALESCE(rs.price, rc.price) AS price
                FROM date_spine ds
                CROSS JOIN hours_t h
                CROSS JOIN (SELECT DISTINCT item_id FROM v_recipe_reagent WHERE recipe_id = ?) rr
                LEFT JOIN reagent_sel rs
                       ON rs.stat_date = ds.stat_date AND rs.hour_of_day = h.hour_of_day AND rs.item_id = rr.item_id
                LEFT JOIN reagent_com rc
                       ON rc.stat_date = ds.stat_date AND rc.hour_of_day = h.hour_of_day AND rc.item_id = rr.item_id
            ),
            reagent_cost AS (
                SELECT ds.stat_date, h.hour_of_day,
                       SUM(CASE WHEN rp.price IS NULL THEN 1 ELSE 0 END) AS missing_reagents,
                       SUM(COALESCE(rp.price, 0) * rr.quantity) AS partial_cost
                FROM date_spine ds
                CROSS JOIN hours_t h
                JOIN v_recipe_reagent rr ON rr.recipe_id = ?
                LEFT JOIN reagent_price rp
                       ON rp.stat_date = ds.stat_date AND rp.hour_of_day = h.hour_of_day AND rp.item_id = rr.item_id
                GROUP BY ds.stat_date, h.hour_of_day
            ),
            output_sel_priced AS (
                SELECT ash.date AS stat_date, h.hour_of_day,
                       ash.bonus_key, ash.modifier_key, ash.pet_species_id,
                       $priceCase AS output_unit_price
                FROM hours_t h
                JOIN auction_stats_hourly ash
                  ON ash.connected_realm_id = ?
                 AND ash.date BETWEEN ? AND ?
                 AND ash.item_id = ?
                 $variantWhere
                HAVING output_unit_price IS NOT NULL
            ),
            output_sel_ranked AS (
                SELECT stat_date, hour_of_day, output_unit_price,
                       ROW_NUMBER() OVER (
                           PARTITION BY stat_date, hour_of_day
                           ORDER BY output_unit_price ASC, bonus_key, modifier_key, pet_species_id
                       ) AS rn
                FROM output_sel_priced
            ),
            output_sel AS (
                SELECT stat_date, hour_of_day, output_unit_price FROM output_sel_ranked WHERE rn = 1
            ),
            output_com_priced AS (
                SELECT ash.date AS stat_date, h.hour_of_day,
                       ash.bonus_key, ash.modifier_key, ash.pet_species_id,
                       $priceCase AS output_unit_price
                FROM hours_t h
                JOIN auction_stats_hourly ash
                  ON ash.connected_realm_id = ?
                 AND ash.date BETWEEN ? AND ?
                 AND ash.item_id = ?
                 $variantWhere
                HAVING output_unit_price IS NOT NULL
            ),
            output_com_ranked AS (
                SELECT stat_date, hour_of_day, output_unit_price,
                       ROW_NUMBER() OVER (
                           PARTITION BY stat_date, hour_of_day
                           ORDER BY output_unit_price ASC, bonus_key, modifier_key, pet_species_id
                       ) AS rn
                FROM output_com_priced
            ),
            output_com AS (
                SELECT stat_date, hour_of_day, output_unit_price FROM output_com_ranked WHERE rn = 1
            ),
            output_price AS (
                SELECT ds.stat_date, h.hour_of_day,
                       COALESCE(os.output_unit_price, oc.output_unit_price) AS output_unit_price
                FROM date_spine ds
                CROSS JOIN hours_t h
                LEFT JOIN output_sel os ON os.stat_date = ds.stat_date AND os.hour_of_day = h.hour_of_day
                LEFT JOIN output_com oc ON oc.stat_date = ds.stat_date AND oc.hour_of_day = h.hour_of_day
            ),
            recipe_dim AS (
                SELECT COALESCE(NULLIF(crafted_quantity, 0), 1) AS crafted_quantity
                FROM v_recipe_crafted_output WHERE recipe_id = ? AND crafted_item_id = ?
            ),
            cells AS (
                SELECT WEEKDAY(ds.stat_date) AS day_of_week,
                       h.hour_of_day,
                       op.output_unit_price,
                       CASE WHEN rc.missing_reagents = 0 AND rc.partial_cost IS NOT NULL AND op.output_unit_price IS NOT NULL
                            THEN op.output_unit_price * rd.crafted_quantity - rc.partial_cost ELSE NULL END AS profit,
                       CASE WHEN rc.missing_reagents = 0 AND rc.partial_cost IS NOT NULL AND rc.partial_cost > 0 AND op.output_unit_price IS NOT NULL
                            THEN 100.0 * (op.output_unit_price * rd.crafted_quantity - rc.partial_cost) / rc.partial_cost ELSE NULL END AS roi_percent
                FROM date_spine ds
                CROSS JOIN hours_t h
                CROSS JOIN recipe_dim rd
                LEFT JOIN reagent_cost rc ON rc.stat_date = ds.stat_date AND rc.hour_of_day = h.hour_of_day
                LEFT JOIN output_price op ON op.stat_date = ds.stat_date AND op.hour_of_day = h.hour_of_day
            )
            SELECT day_of_week,
                   hour_of_day,
                   AVG(profit) AS profit,
                   AVG(output_unit_price) AS output_unit_price,
                   AVG(roi_percent) AS roi_percent,
                   COUNT(profit) AS sample_count
            FROM cells
            WHERE profit IS NOT NULL
            GROUP BY day_of_week, hour_of_day
            ORDER BY day_of_week, hour_of_day
            """.trimIndent()

        val variantParams: Array<Any?> = if (variant) arrayOf(bonusKey, modifierKey, petSpeciesId) else emptyArray()
        return jdbcTemplate.query(
            sql,
            AuctionMarketItemDetailRowMappers.craftingAnalyticsHeatmapRowMapper,
            Date.valueOf(fromDate),
            Date.valueOf(toDate),
            connectedRealmId,
            Date.valueOf(fromDate),
            Date.valueOf(toDate),
            recipeId,
            commodityConnectedRealmId,
            Date.valueOf(fromDate),
            Date.valueOf(toDate),
            recipeId,
            recipeId,
            recipeId,
            connectedRealmId,
            Date.valueOf(fromDate),
            Date.valueOf(toDate),
            itemId,
            *variantParams,
            commodityConnectedRealmId,
            Date.valueOf(fromDate),
            Date.valueOf(toDate),
            itemId,
            *variantParams,
            recipeId,
            itemId,
        )
    }

    private fun hourColumnSuffix(hourOfDay: Int): String = hourOfDay.coerceIn(0, 23).toString().padStart(2, '0')

    private fun hoursCteSql(): String = (0..23).joinToString(separator = " UNION ALL ") { "SELECT $it AS hour_of_day" }

    /**
     * Returns a `CASE h.hour_of_day WHEN 0 THEN ash.price00 ... WHEN 23 THEN ash.price23 END` expression
     * used to unpivot the 24 hourly price columns of `auction_stats_hourly` into a single column when
     * cross-joined with the `hours_t` 0..23 table.
     */
    private fun hourlyPriceCaseExpression(
        tableAlias: String = "ash",
        hourCol: String = "h.hour_of_day",
    ): String =
        (0..23).joinToString(
            prefix = "CASE $hourCol ",
            separator = " ",
            postfix = " END",
        ) { "WHEN $it THEN $tableAlias.price${hourColumnSuffix(it)}" }

    private fun hourlyQuantityCaseExpression(
        tableAlias: String = "ash",
        hourCol: String = "h.hour_of_day",
    ): String =
        (0..23).joinToString(
            prefix = "CASE $hourCol ",
            separator = " ",
            postfix = " END",
        ) { "WHEN $it THEN $tableAlias.quantity${hourColumnSuffix(it)}" }
}
