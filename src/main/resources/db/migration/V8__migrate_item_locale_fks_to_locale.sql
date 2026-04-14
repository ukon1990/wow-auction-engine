DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_item_locale_fk$$

CREATE PROCEDURE migrate_item_locale_fk(
    IN in_table_name VARCHAR(64),
    IN in_column_name VARCHAR(64),
    IN in_fk_name VARCHAR(64)
)
BEGIN
    DECLARE has_locale_table INT DEFAULT 0;
    DECLARE has_target_column INT DEFAULT 0;
    DECLARE old_fk_name VARCHAR(64) DEFAULT NULL;
    DECLARE current_fk_name VARCHAR(64) DEFAULT NULL;

    SELECT COUNT(*)
    INTO has_locale_table
    FROM information_schema.TABLES
    WHERE table_schema = DATABASE()
      AND table_name = 'locale';

    SELECT COUNT(*)
    INTO has_target_column
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = in_table_name
      AND column_name = in_column_name;

    IF has_locale_table > 0 AND has_target_column > 0 THEN
        item_locale_dbo_fk_loop: LOOP
            SET old_fk_name = NULL;

            SELECT constraint_name
            INTO old_fk_name
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE table_schema = DATABASE()
              AND table_name = in_table_name
              AND column_name = in_column_name
              AND referenced_table_name = 'locale_dbo'
            LIMIT 1;

            IF old_fk_name IS NULL THEN
                LEAVE item_locale_dbo_fk_loop;
            END IF;

            SET @drop_old_fk_sql = CONCAT(
                'ALTER TABLE `', in_table_name, '` DROP FOREIGN KEY `', old_fk_name, '`'
            );
            PREPARE drop_old_fk_stmt FROM @drop_old_fk_sql;
            EXECUTE drop_old_fk_stmt;
            DEALLOCATE PREPARE drop_old_fk_stmt;
        END LOOP;

        SET current_fk_name = NULL;

        SELECT constraint_name
        INTO current_fk_name
        FROM information_schema.KEY_COLUMN_USAGE
        WHERE table_schema = DATABASE()
          AND table_name = in_table_name
          AND column_name = in_column_name
          AND referenced_table_name = 'locale'
        LIMIT 1;

        IF current_fk_name IS NULL THEN
            SET @add_locale_fk_sql = CONCAT(
                'ALTER TABLE `', in_table_name, '` ',
                'ADD CONSTRAINT `', in_fk_name, '` ',
                'FOREIGN KEY (`', in_column_name, '`) REFERENCES `locale` (`id`)'
            );
            PREPARE add_locale_fk_stmt FROM @add_locale_fk_sql;
            EXECUTE add_locale_fk_stmt;
            DEALLOCATE PREPARE add_locale_fk_stmt;
        END IF;
    END IF;
END$$

DELIMITER ;

CALL migrate_item_locale_fk('item_quality', 'name_id', 'fk_item_quality_name_locale');
CALL migrate_item_locale_fk('inventory_type', 'name_id', 'fk_inventory_type_name_locale');
CALL migrate_item_locale_fk('item_binding', 'name_id', 'fk_item_binding_name_locale');
CALL migrate_item_locale_fk('item_class', 'name_id', 'fk_item_class_name_locale');
CALL migrate_item_locale_fk('item_subclass', 'display_name_id', 'fk_item_subclass_display_name_locale');
CALL migrate_item_locale_fk('item_summary', 'name_id', 'fk_item_summary_name_locale');
CALL migrate_item_locale_fk('item', 'name_id', 'fk_item_name_locale');

DROP PROCEDURE migrate_item_locale_fk;
