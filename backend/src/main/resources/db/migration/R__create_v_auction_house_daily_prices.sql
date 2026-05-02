SET @daily_exists := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'auction_stats_daily'
);

SET @sql := IF(
    @daily_exists > 0,
    'CREATE OR REPLACE VIEW v_auction_house_daily_prices AS
    WITH daily_prices AS (
        SELECT
            a.connected_realm_id,
            a.item_id,
            a.bonus_key,
            a.modifier_key,
            a.pet_species_id,
            DATE_ADD(
                DATE_SUB(a.date, INTERVAL DAYOFMONTH(a.date) - 1 DAY),
                INTERVAL n.idx - 1 DAY
            ) AS date,
            CASE n.idx
                    WHEN 1 THEN a.min01
                    WHEN 2 THEN a.min02
                    WHEN 3 THEN a.min03
                    WHEN 4 THEN a.min04
                    WHEN 5 THEN a.min05
                    WHEN 6 THEN a.min06
                    WHEN 7 THEN a.min07
                    WHEN 8 THEN a.min08
                    WHEN 9 THEN a.min09
                    WHEN 10 THEN a.min10
                    WHEN 11 THEN a.min11
                    WHEN 12 THEN a.min12
                    WHEN 13 THEN a.min13
                    WHEN 14 THEN a.min14
                    WHEN 15 THEN a.min15
                    WHEN 16 THEN a.min16
                    WHEN 17 THEN a.min17
                    WHEN 18 THEN a.min18
                    WHEN 19 THEN a.min19
                    WHEN 20 THEN a.min20
                    WHEN 21 THEN a.min21
                    WHEN 22 THEN a.min22
                    WHEN 23 THEN a.min23
                    WHEN 24 THEN a.min24
                    WHEN 25 THEN a.min25
                    WHEN 26 THEN a.min26
                    WHEN 27 THEN a.min27
                    WHEN 28 THEN a.min28
                    WHEN 29 THEN a.min29
                    WHEN 30 THEN a.min30
                    WHEN 31 THEN a.min31
                END AS min_price,
            CASE n.idx
                    WHEN 1 THEN a.avg01
                    WHEN 2 THEN a.avg02
                    WHEN 3 THEN a.avg03
                    WHEN 4 THEN a.avg04
                    WHEN 5 THEN a.avg05
                    WHEN 6 THEN a.avg06
                    WHEN 7 THEN a.avg07
                    WHEN 8 THEN a.avg08
                    WHEN 9 THEN a.avg09
                    WHEN 10 THEN a.avg10
                    WHEN 11 THEN a.avg11
                    WHEN 12 THEN a.avg12
                    WHEN 13 THEN a.avg13
                    WHEN 14 THEN a.avg14
                    WHEN 15 THEN a.avg15
                    WHEN 16 THEN a.avg16
                    WHEN 17 THEN a.avg17
                    WHEN 18 THEN a.avg18
                    WHEN 19 THEN a.avg19
                    WHEN 20 THEN a.avg20
                    WHEN 21 THEN a.avg21
                    WHEN 22 THEN a.avg22
                    WHEN 23 THEN a.avg23
                    WHEN 24 THEN a.avg24
                    WHEN 25 THEN a.avg25
                    WHEN 26 THEN a.avg26
                    WHEN 27 THEN a.avg27
                    WHEN 28 THEN a.avg28
                    WHEN 29 THEN a.avg29
                    WHEN 30 THEN a.avg30
                    WHEN 31 THEN a.avg31
                END AS avg_price,
            CASE n.idx
                    WHEN 1 THEN a.price_percentile_25_01
                    WHEN 2 THEN a.price_percentile_25_02
                    WHEN 3 THEN a.price_percentile_25_03
                    WHEN 4 THEN a.price_percentile_25_04
                    WHEN 5 THEN a.price_percentile_25_05
                    WHEN 6 THEN a.price_percentile_25_06
                    WHEN 7 THEN a.price_percentile_25_07
                    WHEN 8 THEN a.price_percentile_25_08
                    WHEN 9 THEN a.price_percentile_25_09
                    WHEN 10 THEN a.price_percentile_25_10
                    WHEN 11 THEN a.price_percentile_25_11
                    WHEN 12 THEN a.price_percentile_25_12
                    WHEN 13 THEN a.price_percentile_25_13
                    WHEN 14 THEN a.price_percentile_25_14
                    WHEN 15 THEN a.price_percentile_25_15
                    WHEN 16 THEN a.price_percentile_25_16
                    WHEN 17 THEN a.price_percentile_25_17
                    WHEN 18 THEN a.price_percentile_25_18
                    WHEN 19 THEN a.price_percentile_25_19
                    WHEN 20 THEN a.price_percentile_25_20
                    WHEN 21 THEN a.price_percentile_25_21
                    WHEN 22 THEN a.price_percentile_25_22
                    WHEN 23 THEN a.price_percentile_25_23
                    WHEN 24 THEN a.price_percentile_25_24
                    WHEN 25 THEN a.price_percentile_25_25
                    WHEN 26 THEN a.price_percentile_25_26
                    WHEN 27 THEN a.price_percentile_25_27
                    WHEN 28 THEN a.price_percentile_25_28
                    WHEN 29 THEN a.price_percentile_25_29
                    WHEN 30 THEN a.price_percentile_25_30
                    WHEN 31 THEN a.price_percentile_25_31
                END AS median_price_25,
            CASE n.idx
                    WHEN 1 THEN a.price_percentile_75_01
                    WHEN 2 THEN a.price_percentile_75_02
                    WHEN 3 THEN a.price_percentile_75_03
                    WHEN 4 THEN a.price_percentile_75_04
                    WHEN 5 THEN a.price_percentile_75_05
                    WHEN 6 THEN a.price_percentile_75_06
                    WHEN 7 THEN a.price_percentile_75_07
                    WHEN 8 THEN a.price_percentile_75_08
                    WHEN 9 THEN a.price_percentile_75_09
                    WHEN 10 THEN a.price_percentile_75_10
                    WHEN 11 THEN a.price_percentile_75_11
                    WHEN 12 THEN a.price_percentile_75_12
                    WHEN 13 THEN a.price_percentile_75_13
                    WHEN 14 THEN a.price_percentile_75_14
                    WHEN 15 THEN a.price_percentile_75_15
                    WHEN 16 THEN a.price_percentile_75_16
                    WHEN 17 THEN a.price_percentile_75_17
                    WHEN 18 THEN a.price_percentile_75_18
                    WHEN 19 THEN a.price_percentile_75_19
                    WHEN 20 THEN a.price_percentile_75_20
                    WHEN 21 THEN a.price_percentile_75_21
                    WHEN 22 THEN a.price_percentile_75_22
                    WHEN 23 THEN a.price_percentile_75_23
                    WHEN 24 THEN a.price_percentile_75_24
                    WHEN 25 THEN a.price_percentile_75_25
                    WHEN 26 THEN a.price_percentile_75_26
                    WHEN 27 THEN a.price_percentile_75_27
                    WHEN 28 THEN a.price_percentile_75_28
                    WHEN 29 THEN a.price_percentile_75_29
                    WHEN 30 THEN a.price_percentile_75_30
                    WHEN 31 THEN a.price_percentile_75_31
                END AS median_price_75,
            CASE n.idx
                    WHEN 1 THEN a.max01
                    WHEN 2 THEN a.max02
                    WHEN 3 THEN a.max03
                    WHEN 4 THEN a.max04
                    WHEN 5 THEN a.max05
                    WHEN 6 THEN a.max06
                    WHEN 7 THEN a.max07
                    WHEN 8 THEN a.max08
                    WHEN 9 THEN a.max09
                    WHEN 10 THEN a.max10
                    WHEN 11 THEN a.max11
                    WHEN 12 THEN a.max12
                    WHEN 13 THEN a.max13
                    WHEN 14 THEN a.max14
                    WHEN 15 THEN a.max15
                    WHEN 16 THEN a.max16
                    WHEN 17 THEN a.max17
                    WHEN 18 THEN a.max18
                    WHEN 19 THEN a.max19
                    WHEN 20 THEN a.max20
                    WHEN 21 THEN a.max21
                    WHEN 22 THEN a.max22
                    WHEN 23 THEN a.max23
                    WHEN 24 THEN a.max24
                    WHEN 25 THEN a.max25
                    WHEN 26 THEN a.max26
                    WHEN 27 THEN a.max27
                    WHEN 28 THEN a.max28
                    WHEN 29 THEN a.max29
                    WHEN 30 THEN a.max30
                    WHEN 31 THEN a.max31
                END AS max_price,
            CASE n.idx
                    WHEN 1 THEN a.min_quantity01
                    WHEN 2 THEN a.min_quantity02
                    WHEN 3 THEN a.min_quantity03
                    WHEN 4 THEN a.min_quantity04
                    WHEN 5 THEN a.min_quantity05
                    WHEN 6 THEN a.min_quantity06
                    WHEN 7 THEN a.min_quantity07
                    WHEN 8 THEN a.min_quantity08
                    WHEN 9 THEN a.min_quantity09
                    WHEN 10 THEN a.min_quantity10
                    WHEN 11 THEN a.min_quantity11
                    WHEN 12 THEN a.min_quantity12
                    WHEN 13 THEN a.min_quantity13
                    WHEN 14 THEN a.min_quantity14
                    WHEN 15 THEN a.min_quantity15
                    WHEN 16 THEN a.min_quantity16
                    WHEN 17 THEN a.min_quantity17
                    WHEN 18 THEN a.min_quantity18
                    WHEN 19 THEN a.min_quantity19
                    WHEN 20 THEN a.min_quantity20
                    WHEN 21 THEN a.min_quantity21
                    WHEN 22 THEN a.min_quantity22
                    WHEN 23 THEN a.min_quantity23
                    WHEN 24 THEN a.min_quantity24
                    WHEN 25 THEN a.min_quantity25
                    WHEN 26 THEN a.min_quantity26
                    WHEN 27 THEN a.min_quantity27
                    WHEN 28 THEN a.min_quantity28
                    WHEN 29 THEN a.min_quantity29
                    WHEN 30 THEN a.min_quantity30
                    WHEN 31 THEN a.min_quantity31
                END AS min_quantity,
            CASE n.idx
                    WHEN 1 THEN a.avg_quantity01
                    WHEN 2 THEN a.avg_quantity02
                    WHEN 3 THEN a.avg_quantity03
                    WHEN 4 THEN a.avg_quantity04
                    WHEN 5 THEN a.avg_quantity05
                    WHEN 6 THEN a.avg_quantity06
                    WHEN 7 THEN a.avg_quantity07
                    WHEN 8 THEN a.avg_quantity08
                    WHEN 9 THEN a.avg_quantity09
                    WHEN 10 THEN a.avg_quantity10
                    WHEN 11 THEN a.avg_quantity11
                    WHEN 12 THEN a.avg_quantity12
                    WHEN 13 THEN a.avg_quantity13
                    WHEN 14 THEN a.avg_quantity14
                    WHEN 15 THEN a.avg_quantity15
                    WHEN 16 THEN a.avg_quantity16
                    WHEN 17 THEN a.avg_quantity17
                    WHEN 18 THEN a.avg_quantity18
                    WHEN 19 THEN a.avg_quantity19
                    WHEN 20 THEN a.avg_quantity20
                    WHEN 21 THEN a.avg_quantity21
                    WHEN 22 THEN a.avg_quantity22
                    WHEN 23 THEN a.avg_quantity23
                    WHEN 24 THEN a.avg_quantity24
                    WHEN 25 THEN a.avg_quantity25
                    WHEN 26 THEN a.avg_quantity26
                    WHEN 27 THEN a.avg_quantity27
                    WHEN 28 THEN a.avg_quantity28
                    WHEN 29 THEN a.avg_quantity29
                    WHEN 30 THEN a.avg_quantity30
                    WHEN 31 THEN a.avg_quantity31
                END AS avg_quantity,
            CASE n.idx
                    WHEN 1 THEN a.max_quantity01
                    WHEN 2 THEN a.max_quantity02
                    WHEN 3 THEN a.max_quantity03
                    WHEN 4 THEN a.max_quantity04
                    WHEN 5 THEN a.max_quantity05
                    WHEN 6 THEN a.max_quantity06
                    WHEN 7 THEN a.max_quantity07
                    WHEN 8 THEN a.max_quantity08
                    WHEN 9 THEN a.max_quantity09
                    WHEN 10 THEN a.max_quantity10
                    WHEN 11 THEN a.max_quantity11
                    WHEN 12 THEN a.max_quantity12
                    WHEN 13 THEN a.max_quantity13
                    WHEN 14 THEN a.max_quantity14
                    WHEN 15 THEN a.max_quantity15
                    WHEN 16 THEN a.max_quantity16
                    WHEN 17 THEN a.max_quantity17
                    WHEN 18 THEN a.max_quantity18
                    WHEN 19 THEN a.max_quantity19
                    WHEN 20 THEN a.max_quantity20
                    WHEN 21 THEN a.max_quantity21
                    WHEN 22 THEN a.max_quantity22
                    WHEN 23 THEN a.max_quantity23
                    WHEN 24 THEN a.max_quantity24
                    WHEN 25 THEN a.max_quantity25
                    WHEN 26 THEN a.max_quantity26
                    WHEN 27 THEN a.max_quantity27
                    WHEN 28 THEN a.max_quantity28
                    WHEN 29 THEN a.max_quantity29
                    WHEN 30 THEN a.max_quantity30
                    WHEN 31 THEN a.max_quantity31
                END AS max_quantity
        FROM auction_stats_daily a
                 JOIN (
            SELECT 1 AS idx UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30 UNION ALL SELECT 31
        ) n
    )
    SELECT *
    FROM daily_prices
    WHERE min_price IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
