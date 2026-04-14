DELIMITER $$

DROP PROCEDURE IF EXISTS drop_unique_indexes_for_column$$

CREATE PROCEDURE drop_unique_indexes_for_column(
    IN in_table_name VARCHAR(64),
    IN in_column_name VARCHAR(64)
)
drop_unique_indexes_proc: BEGIN
    DECLARE target_table_exists INT DEFAULT 0;
    DECLARE target_index_name VARCHAR(64) DEFAULT NULL;

    SELECT COUNT(*)
    INTO target_table_exists
    FROM information_schema.TABLES
    WHERE table_schema = DATABASE()
      AND table_name = in_table_name;

    IF target_table_exists = 0 THEN
        LEAVE drop_unique_indexes_proc;
    END IF;

    drop_index_loop: LOOP
        SET target_index_name = NULL;

        SELECT index_name
        INTO target_index_name
        FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE()
          AND table_name = in_table_name
          AND column_name = in_column_name
          AND non_unique = 0
          AND index_name <> 'PRIMARY'
        LIMIT 1;

        IF target_index_name IS NULL THEN
            LEAVE drop_index_loop;
        END IF;

        SET @drop_index_sql = CONCAT(
            'ALTER TABLE `', in_table_name, '` DROP INDEX `', target_index_name, '`'
        );
        PREPARE drop_index_stmt FROM @drop_index_sql;
        EXECUTE drop_index_stmt;
        DEALLOCATE PREPARE drop_index_stmt;
    END LOOP;
END$$

DROP PROCEDURE IF EXISTS ensure_non_unique_index_for_column$$

CREATE PROCEDURE ensure_non_unique_index_for_column(
    IN in_table_name VARCHAR(64),
    IN in_column_name VARCHAR(64),
    IN in_index_name VARCHAR(64)
)
ensure_non_unique_index_proc: BEGIN
    DECLARE target_table_exists INT DEFAULT 0;
    DECLARE existing_index_name VARCHAR(64) DEFAULT NULL;

    SELECT COUNT(*)
    INTO target_table_exists
    FROM information_schema.TABLES
    WHERE table_schema = DATABASE()
      AND table_name = in_table_name;

    IF target_table_exists = 0 THEN
        LEAVE ensure_non_unique_index_proc;
    END IF;

    SELECT index_name
    INTO existing_index_name
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = in_table_name
      AND column_name = in_column_name
      AND index_name = in_index_name
    LIMIT 1;

    IF existing_index_name IS NULL THEN
        SET @create_index_sql = CONCAT(
            'ALTER TABLE `', in_table_name, '` ADD INDEX `', in_index_name, '` (`', in_column_name, '`)'
        );
        PREPARE create_index_stmt FROM @create_index_sql;
        EXECUTE create_index_stmt;
        DEALLOCATE PREPARE create_index_stmt;
    END IF;
END$$

DELIMITER ;

CALL ensure_non_unique_index_for_column('item', 'item_class_id', 'idx_item_item_class_id');
CALL drop_unique_indexes_for_column('item', 'item_class_id');

DROP PROCEDURE ensure_non_unique_index_for_column;
DROP PROCEDURE drop_unique_indexes_for_column;
