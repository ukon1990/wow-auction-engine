SET @hourly_exists := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'auction_stats_hourly'
);

SET @sql := IF(
    @hourly_exists > 0,
    'CREATE OR REPLACE VIEW v_auction_house_daily_prices AS
    WITH daily_prices AS (
    SELECT
        a.connected_realm_id,
        a.item_id,
        a.bonus_key,
        a.modifier_key,
        a.pet_species_id,
        DATEADD(n.idx, n.idx + 1) AS date,
        n.idx AS day_of_month,
        CASE n.idx
            WHEN 0 THEN a.price00
            WHEN 1 THEN a.price01
            WHEN 2 THEN a.price02
            WHEN 3 THEN a.price03
            WHEN 4 THEN a.price04
            WHEN 5 THEN a.price05
            WHEN 6 THEN a.price06
            WHEN 7 THEN a.price07
            WHEN 8 THEN a.price08
            WHEN 9 THEN a.price09
            WHEN 10 THEN a.price10
            WHEN 11 THEN a.price11
            WHEN 12 THEN a.price12
            WHEN 13 THEN a.price13
            WHEN 14 THEN a.price14
            WHEN 15 THEN a.price15
            WHEN 16 THEN a.price16
            WHEN 17 THEN a.price17
            WHEN 18 THEN a.price18
            WHEN 19 THEN a.price19
            WHEN 20 THEN a.price20
            WHEN 21 THEN a.price21
            WHEN 22 THEN a.price22
            WHEN 23 THEN a.price23
            END AS minPrice,
        CASE n.idx
            WHEN 0 THEN a.quantity00
            WHEN 1 THEN a.quantity01
            WHEN 2 THEN a.quantity02
            WHEN 3 THEN a.quantity03
            WHEN 4 THEN a.quantity04
            WHEN 5 THEN a.quantity05
            WHEN 6 THEN a.quantity06
            WHEN 7 THEN a.quantity07
            WHEN 8 THEN a.quantity08
            WHEN 9 THEN a.quantity09
            WHEN 10 THEN a.quantity10
            WHEN 11 THEN a.quantity11
            WHEN 12 THEN a.quantity12
            WHEN 13 THEN a.quantity13
            WHEN 14 THEN a.quantity14
            WHEN 15 THEN a.quantity15
            WHEN 16 THEN a.quantity16
            WHEN 17 THEN a.quantity17
            WHEN 18 THEN a.quantity18
            WHEN 19 THEN a.quantity19
            WHEN 20 THEN a.quantity20
            WHEN 21 THEN a.quantity21
            WHEN 22 THEN a.quantity22
            WHEN 23 THEN a.quantity23
            END AS quantity
    FROM auction_stats_hourly a
             JOIN (
        SELECT 0 AS idx UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
        UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
        UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
        UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
        UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
        UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23
        UNION ALL SELECT 24 UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27
        UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30 UNION ALL SELECT 31
    ) n
    )
    SELECT *
    FROM hourly_prices
    WHERE price IS NOT NULL',
    'SELECT 1'
            );
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
