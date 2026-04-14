DELIMITER $$

DROP PROCEDURE IF EXISTS ensure_index_if_table_exists$$

CREATE PROCEDURE ensure_index_if_table_exists(
    IN in_table_name VARCHAR(64),
    IN in_index_name VARCHAR(64),
    IN in_index_columns VARCHAR(255)
)
ensure_index_proc: BEGIN
    DECLARE target_table_exists INT DEFAULT 0;
    DECLARE existing_index_name VARCHAR(64) DEFAULT NULL;

    SELECT COUNT(*)
    INTO target_table_exists
    FROM information_schema.TABLES
    WHERE table_schema = DATABASE()
      AND table_name = in_table_name;

    IF target_table_exists = 0 THEN
        LEAVE ensure_index_proc;
    END IF;

    SELECT index_name
    INTO existing_index_name
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = in_table_name
      AND index_name = in_index_name
    LIMIT 1;

    IF existing_index_name IS NULL THEN
        SET @create_index_sql = CONCAT(
            'ALTER TABLE `', in_table_name, '` ADD INDEX `', in_index_name, '` ', in_index_columns
        );
        PREPARE create_index_stmt FROM @create_index_sql;
        EXECUTE create_index_stmt;
        DEALLOCATE PREPARE create_index_stmt;
    END IF;
END$$

DELIMITER ;

CALL ensure_index_if_table_exists(
    'hourly_auction_stats',
    'idx_hourly_auction_stats_date_pet_species_item_id',
    '(date, pet_species_id, item_id)'
);
CALL ensure_index_if_table_exists(
    'recipe',
    'idx_recipe_crafted_item_id',
    '(crafted_item_id)'
);
CALL ensure_index_if_table_exists(
    'recipe_reagent',
    'idx_recipe_reagent_item_id',
    '(item_id)'
);

DROP PROCEDURE ensure_index_if_table_exists;
