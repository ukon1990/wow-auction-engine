SET @hourly_exists := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'hourly_auction_stats'
);

SET @hourly_has_ah_type_id := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'hourly_auction_stats'
      AND column_name = 'ah_type_id'
);

SET @daily_exists := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'daily_auction_stats'
);

SET @daily_has_ah_type_id := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'daily_auction_stats'
      AND column_name = 'ah_type_id'
);

SET @sql := IF(
    @hourly_exists > 0 AND @hourly_has_ah_type_id = 0,
    'ALTER TABLE hourly_auction_stats ADD COLUMN ah_type_id INT NOT NULL DEFAULT 0 AFTER connected_realm_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    @daily_exists > 0 AND @daily_has_ah_type_id = 0,
    'ALTER TABLE daily_auction_stats ADD COLUMN ah_type_id INT NOT NULL DEFAULT 0 AFTER connected_realm_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
