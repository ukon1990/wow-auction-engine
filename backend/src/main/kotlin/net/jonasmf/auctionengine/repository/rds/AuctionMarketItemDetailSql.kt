package net.jonasmf.auctionengine.repository.rds

import java.sql.Date
import java.time.LocalDate

internal object AuctionMarketItemDetailSql {
    internal fun buildCurrentListingsSqlAndArgs(
        connectedRealmId: Int,
        itemId: Int,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): Pair<String, Array<Any?>> {
        val variantWhere =
            if (variant) {
                """
                AND a.bonus_key <=> ?
                AND a.modifier_key <=> ?
                AND COALESCE(a.pet_species_id, -1) = ?
                """.trimIndent()
            } else {
                ""
            }
        val sql =
            """
            WITH latest_history AS (
                SELECT MAX(id) AS update_history_id
                FROM connected_realm_update_history
                WHERE connected_realm_id = ?
            )
            SELECT
                ap.buyout AS price,
                SUM(ap.quantity) AS quantity
            FROM auction_price ap
            INNER JOIN auction a ON a.id = ap.auction_id
            INNER JOIN latest_history lh
                ON lh.update_history_id = a.update_history_id
               AND lh.update_history_id = ap.update_history_id
            WHERE a.connected_realm_id = ?
              AND a.item_id = ?
              AND a.buyout IS NOT NULL
              AND ap.buyout IS NOT NULL
              $variantWhere
            GROUP BY ap.buyout
            ORDER BY ap.buyout ASC
            """.trimIndent()
        val params: Array<Any?> =
            if (variant) {
                arrayOf(
                    connectedRealmId,
                    connectedRealmId,
                    itemId,
                    bonusKey,
                    modifierKey,
                    petSpeciesId,
                )
            } else {
                arrayOf(
                    connectedRealmId,
                    connectedRealmId,
                    itemId,
                )
            }
        return sql to params
    }

    internal fun buildDailySqlAndArgs(
        connectedRealmId: Int,
        itemId: Int,
        fromDate: LocalDate,
        toDate: LocalDate,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): Pair<String, Array<Any?>> {
        val sql =
            if (variant) {
                """
                WITH RECURSIVE date_spine AS (
                    SELECT ? AS stat_date
                    UNION ALL
                    SELECT DATE_ADD(stat_date, INTERVAL 1 DAY)
                    FROM date_spine
                    WHERE stat_date < ?
                ),
                priced AS (
                    SELECT
                        v.date,
                        v.price,
                        v.quantity,
                        PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY v.price) OVER (
                            PARTITION BY v.connected_realm_id, v.item_id, v.bonus_key, v.modifier_key, v.pet_species_id, v.date
                        ) AS p25_price,
                        PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY v.price) OVER (
                            PARTITION BY v.connected_realm_id, v.item_id, v.bonus_key, v.modifier_key, v.pet_species_id, v.date
                        ) AS p75_price
                    FROM v_auction_house_prices v
                    WHERE v.connected_realm_id = ?
                      AND v.item_id = ?
                      AND v.date BETWEEN ? AND ?
                      AND v.bonus_key <=> ?
                      AND v.modifier_key <=> ?
                      AND v.pet_species_id = ?
                ),
                daily_agg AS (
                    SELECT
                        date AS stat_date,
                        MIN(price) AS min_price,
                        AVG(price) AS avg_price,
                        MIN(p25_price) AS p25_price,
                        MIN(p75_price) AS p75_price,
                        MAX(price) AS max_price,
                        MIN(quantity) AS min_quantity,
                        AVG(quantity) AS avg_quantity,
                        MAX(quantity) AS max_quantity
                    FROM priced
                    GROUP BY date
                )
                SELECT
                    ds.stat_date,
                    da.min_price,
                    da.avg_price,
                    da.p25_price,
                    da.p75_price,
                    da.max_price,
                    da.min_quantity,
                    da.avg_quantity,
                    da.max_quantity
                FROM date_spine ds
                LEFT JOIN daily_agg da ON da.stat_date = ds.stat_date
                ORDER BY ds.stat_date
                """.trimIndent()
            } else {
                """
                WITH RECURSIVE date_spine AS (
                    SELECT ? AS stat_date
                    UNION ALL
                    SELECT DATE_ADD(stat_date, INTERVAL 1 DAY)
                    FROM date_spine
                    WHERE stat_date < ?
                ),
                priced AS (
                    SELECT
                        v.date,
                        v.price,
                        v.quantity,
                        PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY v.price) OVER (
                            PARTITION BY v.connected_realm_id, v.item_id, v.date
                        ) AS p25_price,
                        PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY v.price) OVER (
                            PARTITION BY v.connected_realm_id, v.item_id, v.date
                        ) AS p75_price
                    FROM v_auction_house_prices v
                    WHERE v.connected_realm_id = ?
                      AND v.item_id = ?
                      AND v.date BETWEEN ? AND ?
                ),
                daily_agg AS (
                    SELECT
                        date AS stat_date,
                        MIN(price) AS min_price,
                        AVG(price) AS avg_price,
                        MIN(p25_price) AS p25_price,
                        MIN(p75_price) AS p75_price,
                        MAX(price) AS max_price,
                        MIN(quantity) AS min_quantity,
                        AVG(quantity) AS avg_quantity,
                        MAX(quantity) AS max_quantity
                    FROM priced
                    GROUP BY date
                )
                SELECT
                    ds.stat_date,
                    da.min_price,
                    da.avg_price,
                    da.p25_price,
                    da.p75_price,
                    da.max_price,
                    da.min_quantity,
                    da.avg_quantity,
                    da.max_quantity
                FROM date_spine ds
                LEFT JOIN daily_agg da ON da.stat_date = ds.stat_date
                ORDER BY ds.stat_date
                """.trimIndent()
            }
        val params: Array<Any?> =
            if (variant) {
                arrayOf(
                    Date.valueOf(fromDate),
                    Date.valueOf(toDate),
                    connectedRealmId,
                    itemId,
                    Date.valueOf(fromDate),
                    Date.valueOf(toDate),
                    bonusKey,
                    modifierKey,
                    petSpeciesId,
                )
            } else {
                arrayOf(
                    Date.valueOf(fromDate),
                    Date.valueOf(toDate),
                    connectedRealmId,
                    itemId,
                    Date.valueOf(fromDate),
                    Date.valueOf(toDate),
                )
            }
        return sql to params
    }

    internal fun buildHourlySqlAndArgs(
        connectedRealmId: Int,
        itemId: Int,
        fromDate: LocalDate,
        toDate: LocalDate,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): Pair<String, Array<Any?>> {
        val priceCase = hourlyPriceCaseExpression("ash", "hs.hour_of_day")
        val quantityCase = hourlyQuantityCaseExpression("ash", "hs.hour_of_day")
        val sql =
            if (variant) {
                """
                WITH RECURSIVE date_spine AS (
                    SELECT ? AS stat_date
                    UNION ALL
                    SELECT DATE_ADD(stat_date, INTERVAL 1 DAY)
                    FROM date_spine
                    WHERE stat_date < ?
                ),
                hour_spine AS (
                    SELECT 0 AS hour_of_day
                    UNION ALL
                    SELECT hour_of_day + 1 FROM hour_spine WHERE hour_of_day < 23
                ),
                hourly_agg AS (
                    SELECT
                        ds.stat_date,
                        hs.hour_of_day,
                        MIN($priceCase) AS min_price,
                        AVG($priceCase) AS avg_price,
                        MAX($priceCase) AS max_price,
                        SUM($quantityCase) AS total_quantity
                    FROM date_spine ds
                    CROSS JOIN hour_spine hs
                    LEFT JOIN auction_stats_hourly ash
                      ON ash.connected_realm_id = ?
                     AND ash.item_id = ?
                     AND ash.date = ds.stat_date
                     AND ash.bonus_key <=> ?
                     AND ash.modifier_key <=> ?
                     AND ash.pet_species_id = ?
                    GROUP BY ds.stat_date, hs.hour_of_day
                )
                SELECT
                    ds.stat_date,
                    hs.hour_of_day,
                    DATE_ADD(TIMESTAMP(ds.stat_date), INTERVAL hs.hour_of_day HOUR) AS timestamp,
                    ha.min_price,
                    ha.avg_price,
                    NULL AS p25_price,
                    NULL AS p75_price,
                    ha.max_price,
                    ha.total_quantity
                FROM date_spine ds
                CROSS JOIN hour_spine hs
                LEFT JOIN hourly_agg ha
                  ON ha.stat_date = ds.stat_date
                 AND ha.hour_of_day = hs.hour_of_day
                ORDER BY ds.stat_date, hs.hour_of_day
                """.trimIndent()
            } else {
                """
                WITH RECURSIVE date_spine AS (
                    SELECT ? AS stat_date
                    UNION ALL
                    SELECT DATE_ADD(stat_date, INTERVAL 1 DAY)
                    FROM date_spine
                    WHERE stat_date < ?
                ),
                hour_spine AS (
                    SELECT 0 AS hour_of_day
                    UNION ALL
                    SELECT hour_of_day + 1 FROM hour_spine WHERE hour_of_day < 23
                ),
                hourly_agg AS (
                    SELECT
                        ds.stat_date,
                        hs.hour_of_day,
                        MIN($priceCase) AS min_price,
                        AVG($priceCase) AS avg_price,
                        MAX($priceCase) AS max_price,
                        SUM($quantityCase) AS total_quantity
                    FROM date_spine ds
                    CROSS JOIN hour_spine hs
                    LEFT JOIN auction_stats_hourly ash
                      ON ash.connected_realm_id = ?
                     AND ash.item_id = ?
                     AND ash.date = ds.stat_date
                    GROUP BY ds.stat_date, hs.hour_of_day
                )
                SELECT
                    ds.stat_date,
                    hs.hour_of_day,
                    DATE_ADD(TIMESTAMP(ds.stat_date), INTERVAL hs.hour_of_day HOUR) AS timestamp,
                    ha.min_price,
                    ha.avg_price,
                    NULL AS p25_price,
                    NULL AS p75_price,
                    ha.max_price,
                    ha.total_quantity
                FROM date_spine ds
                CROSS JOIN hour_spine hs
                LEFT JOIN hourly_agg ha
                  ON ha.stat_date = ds.stat_date
                 AND ha.hour_of_day = hs.hour_of_day
                ORDER BY ds.stat_date, hs.hour_of_day
                """.trimIndent()
            }
        val params: Array<Any?> =
            if (variant) {
                arrayOf(
                    Date.valueOf(fromDate),
                    Date.valueOf(toDate),
                    connectedRealmId,
                    itemId,
                    bonusKey,
                    modifierKey,
                    petSpeciesId,
                )
            } else {
                arrayOf(
                    Date.valueOf(fromDate),
                    Date.valueOf(toDate),
                    connectedRealmId,
                    itemId,
                )
            }
        return sql to params
    }

    internal fun buildPieSqlAndArgs(
        connectedRealmId: Int,
        itemId: Int,
        statDate: LocalDate,
        variant: Boolean,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
    ): Pair<String, Array<Any?>> {
        val whereVariant =
            if (variant) {
                "AND v.bonus_key <=> ? AND v.modifier_key <=> ? AND v.pet_species_id = ?"
            } else {
                ""
            }
        val sql =
            """
            WITH hours AS (
                SELECT v.hour_of_day, SUM(v.quantity) AS qty
                FROM v_auction_house_prices v
                WHERE v.connected_realm_id = ?
                  AND v.item_id = ?
                  AND v.date = ?
                  $whereVariant
                GROUP BY v.hour_of_day
            ),
            total AS (
                SELECT COALESCE(SUM(qty), 0) AS t FROM hours
            )
            SELECT
                h.hour_of_day,
                h.qty AS quantity,
                CASE WHEN total.t > 0 THEN h.qty / total.t ELSE 0 END AS fraction
            FROM hours h
            CROSS JOIN total
            ORDER BY h.hour_of_day
            """.trimIndent()
        val params: Array<Any?> =
            if (variant) {
                arrayOf(
                    connectedRealmId,
                    itemId,
                    Date.valueOf(statDate),
                    bonusKey,
                    modifierKey,
                    petSpeciesId,
                )
            } else {
                arrayOf(
                    connectedRealmId,
                    itemId,
                    Date.valueOf(statDate),
                )
            }
        return sql to params
    }

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
    ): Pair<String, Array<Any?>> {
        val hourSuffix = hourColumnSuffix(hourOfDay)
        val commodityHourSuffix = hourColumnSuffix(commodityHourOfDay)
        val outputWhere =
            if (variant) {
                "AND ash.bonus_key <=> ? AND ash.modifier_key <=> ? AND ash.pet_species_id = ?"
            } else {
                ""
            }
        val sql =
            """
            WITH
            reagent_sel_base AS (
                SELECT ash.item_id, ash.price$hourSuffix AS price, ash.bonus_key, ash.modifier_key, ash.pet_species_id
                FROM auction_stats_hourly ash
                WHERE ash.connected_realm_id = ?
                  AND ash.date = ?
                  AND ash.price$hourSuffix IS NOT NULL
                  AND ash.item_id IN (SELECT DISTINCT rr.item_id FROM v_recipe_reagent rr)
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
                  AND ash.item_id IN (SELECT DISTINCT rr.item_id FROM v_recipe_reagent rr)
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
                FROM (SELECT DISTINCT item_id FROM v_recipe_reagent) items
                LEFT JOIN reagent_sel rs ON rs.item_id = items.item_id
                LEFT JOIN reagent_com rc ON rc.item_id = items.item_id
            ),
            recipe_reagent_agg AS (
                SELECT
                    r.id AS recipe_id,
                    SUM(CASE WHEN rr.internal_id IS NULL THEN 0 WHEN rp.price IS NULL THEN 1 ELSE 0 END) AS missing_reagents,
                    SUM(CASE WHEN rr.internal_id IS NULL THEN 0 ELSE COALESCE(rp.price, 0) * rr.quantity END) AS reagent_cost_partial
                FROM v_recipe r
                INNER JOIN v_recipe_crafted_output target_output
                    ON target_output.recipe_id = r.id
                    AND target_output.crafted_item_id = ?
                LEFT JOIN v_recipe_reagent rr ON rr.recipe_id = r.id
                LEFT JOIN reagent_price rp ON rp.item_id = rr.item_id
                GROUP BY r.id
            ),
            recipe_reagent_cost AS (
                SELECT
                    recipe_id,
                    CASE WHEN missing_reagents > 0 THEN NULL ELSE reagent_cost_partial END AS reagent_cost,
                    missing_reagents = 0 AS reagents_fully_priced
                FROM recipe_reagent_agg
            ),
            output_sel_base AS (
                SELECT ash.item_id, ash.price$hourSuffix AS output_unit_price, ash.bonus_key, ash.modifier_key, ash.pet_species_id
                FROM auction_stats_hourly ash
                WHERE ash.connected_realm_id = ?
                  AND ash.date = ?
                  AND ash.item_id = ?
                  AND ash.price$hourSuffix IS NOT NULL
                  $outputWhere
            ),
            output_sel_ranked AS (
                SELECT output_unit_price,
                       ROW_NUMBER() OVER (ORDER BY output_unit_price ASC, bonus_key, modifier_key, pet_species_id) AS rn
                FROM output_sel_base
            ),
            output_sel AS (
                SELECT output_unit_price FROM output_sel_ranked WHERE rn = 1
            ),
            output_com_base AS (
                SELECT ash.item_id, ash.price$commodityHourSuffix AS output_unit_price, ash.bonus_key, ash.modifier_key, ash.pet_species_id
                FROM auction_stats_hourly ash
                WHERE ash.connected_realm_id = ?
                  AND ash.date = ?
                  AND ash.item_id = ?
                  AND ash.price$commodityHourSuffix IS NOT NULL
                  $outputWhere
            ),
            output_com_ranked AS (
                SELECT output_unit_price,
                       ROW_NUMBER() OVER (ORDER BY output_unit_price ASC, bonus_key, modifier_key, pet_species_id) AS rn
                FROM output_com_base
            ),
            output_com AS (
                SELECT output_unit_price FROM output_com_ranked WHERE rn = 1
            ),
            output_price AS (
                SELECT COALESCE(os.output_unit_price, oc.output_unit_price) AS output_unit_price
                FROM (SELECT 1 AS id) seed
                LEFT JOIN output_sel os ON TRUE
                LEFT JOIN output_com oc ON TRUE
            )
            SELECT
                r.id AS recipe_id,
                r.rank AS recipe_rank,
                COALESCE(l.$localeColumnSuffix, l.en_gb, l.en_us, CAST(r.id AS CHAR)) AS recipe_name,
                r.media_url AS recipe_media_url,
                COALESCE(NULLIF(target_output.crafted_quantity, 0), 1) AS crafted_quantity,
                rrc.reagent_cost,
                COALESCE(rrc.reagents_fully_priced, TRUE) AS reagents_fully_priced,
                op.output_unit_price,
                CASE
                    WHEN rrc.reagents_fully_priced AND rrc.reagent_cost IS NOT NULL AND op.output_unit_price IS NOT NULL
                    THEN op.output_unit_price * COALESCE(NULLIF(target_output.crafted_quantity, 0), 1) - rrc.reagent_cost
                    ELSE NULL
                END AS profit,
                CASE
                    WHEN rrc.reagents_fully_priced AND rrc.reagent_cost IS NOT NULL AND rrc.reagent_cost > 0 AND op.output_unit_price IS NOT NULL
                    THEN 100.0 * (op.output_unit_price * COALESCE(NULLIF(target_output.crafted_quantity, 0), 1) - rrc.reagent_cost) / rrc.reagent_cost
                    ELSE NULL
                END AS roi_percent
            FROM v_recipe r
            INNER JOIN v_recipe_crafted_output target_output
                ON target_output.recipe_id = r.id
                AND target_output.crafted_item_id = ?
            LEFT JOIN recipe_reagent_cost rrc ON rrc.recipe_id = r.id
            LEFT JOIN locale l ON l.id = r.name_id
            LEFT JOIN output_price op ON TRUE
            ORDER BY (r.id = ?) DESC, (profit IS NULL), profit DESC, r.id
            """.trimIndent()
        val outputParams: Array<Any?> =
            if (variant) {
                arrayOf(
                    connectedRealmId,
                    Date.valueOf(statDate),
                    itemId,
                    bonusKey,
                    modifierKey,
                    petSpeciesId,
                    commodityConnectedRealmId,
                    Date.valueOf(commodityStatDate),
                    itemId,
                    bonusKey,
                    modifierKey,
                    petSpeciesId,
                )
            } else {
                arrayOf(
                    connectedRealmId,
                    Date.valueOf(statDate),
                    itemId,
                    commodityConnectedRealmId,
                    Date.valueOf(commodityStatDate),
                    itemId,
                )
            }
        return sql to
            arrayOf(
                connectedRealmId,
                Date.valueOf(statDate),
                commodityConnectedRealmId,
                Date.valueOf(commodityStatDate),
                itemId,
                *outputParams,
                itemId,
                preferredRecipeId ?: -1,
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
