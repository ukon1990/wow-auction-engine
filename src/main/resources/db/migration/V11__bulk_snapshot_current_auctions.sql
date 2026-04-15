SET @auction_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
);

SET @auction_item_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'auction_item'
);

SET @auction_item_modifier_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'auction_item_modifier'
);

SET @legacy_modifier_link_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'auction_item_modifiers'
);

SET @snapshot_tables_exist = IF(
    @auction_exists > 0
    AND @auction_item_exists > 0
    AND @auction_item_modifier_exists > 0,
    1,
    0
);

SET @add_deleted_at_sql = IF(
    @snapshot_tables_exist > 0,
    'ALTER TABLE auction
        ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL AFTER last_seen',
    'SELECT 1'
);
PREPARE add_deleted_at_stmt FROM @add_deleted_at_sql;
EXECUTE add_deleted_at_stmt;
DEALLOCATE PREPARE add_deleted_at_stmt;

SET @add_variant_hash_sql = IF(
    @snapshot_tables_exist > 0,
    'ALTER TABLE auction_item
        ADD COLUMN IF NOT EXISTS variant_hash CHAR(64) NULL AFTER item_id',
    'SELECT 1'
);
PREPARE add_variant_hash_stmt FROM @add_variant_hash_sql;
EXECUTE add_variant_hash_stmt;
DEALLOCATE PREPARE add_variant_hash_stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_auction_modifier_canonical;
SET @create_tmp_auction_modifier_canonical_sql = IF(
    @snapshot_tables_exist > 0,
    'CREATE TEMPORARY TABLE tmp_auction_modifier_canonical AS
     SELECT MIN(id) AS canonical_id, type, value
     FROM auction_item_modifier
     GROUP BY type, value',
    'SELECT 1'
);
PREPARE create_tmp_auction_modifier_canonical_stmt FROM @create_tmp_auction_modifier_canonical_sql;
EXECUTE create_tmp_auction_modifier_canonical_stmt;
DEALLOCATE PREPARE create_tmp_auction_modifier_canonical_stmt;

SET @normalize_legacy_modifier_ids_sql = IF(
    @snapshot_tables_exist > 0 AND @legacy_modifier_link_exists > 0,
    'UPDATE auction_item_modifiers legacy
     JOIN auction_item_modifier modifier_row ON modifier_row.id = legacy.modifiers_id
     JOIN tmp_auction_modifier_canonical canonical
       ON canonical.type = modifier_row.type
      AND canonical.value = modifier_row.value
     SET legacy.modifiers_id = canonical.canonical_id',
    'SELECT 1'
);
PREPARE normalize_legacy_modifier_ids_stmt FROM @normalize_legacy_modifier_ids_sql;
EXECUTE normalize_legacy_modifier_ids_stmt;
DEALLOCATE PREPARE normalize_legacy_modifier_ids_stmt;

SET @delete_duplicate_modifiers_sql = IF(
    @snapshot_tables_exist > 0,
    'DELETE modifier_row
     FROM auction_item_modifier modifier_row
     LEFT JOIN tmp_auction_modifier_canonical canonical
         ON canonical.canonical_id = modifier_row.id
     WHERE canonical.canonical_id IS NULL',
    'SELECT 1'
);
PREPARE delete_duplicate_modifiers_stmt FROM @delete_duplicate_modifiers_sql;
EXECUTE delete_duplicate_modifiers_stmt;
DEALLOCATE PREPARE delete_duplicate_modifiers_stmt;

SET @drop_modifier_unique_sql = IF(
    @snapshot_tables_exist > 0 AND EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auction_item_modifier'
          AND index_name = 'uk_auction_item_modifier_type_value'
    ),
    'DROP INDEX uk_auction_item_modifier_type_value ON auction_item_modifier',
    'SELECT 1'
);
PREPARE drop_modifier_unique_stmt FROM @drop_modifier_unique_sql;
EXECUTE drop_modifier_unique_stmt;
DEALLOCATE PREPARE drop_modifier_unique_stmt;

SET @create_modifier_unique_sql = IF(
    @snapshot_tables_exist > 0,
    'CREATE UNIQUE INDEX uk_auction_item_modifier_type_value
        ON auction_item_modifier (type, value)',
    'SELECT 1'
);
PREPARE create_modifier_unique_stmt FROM @create_modifier_unique_sql;
EXECUTE create_modifier_unique_stmt;
DEALLOCATE PREPARE create_modifier_unique_stmt;

SET @create_modifier_link_table_sql = IF(
    @snapshot_tables_exist > 0,
    'CREATE TABLE IF NOT EXISTS auction_item_modifier_link (
        auction_item_id BIGINT NOT NULL,
        sort_order INT NOT NULL,
        modifier_id BIGINT NOT NULL,
        PRIMARY KEY (auction_item_id, sort_order),
        KEY idx_auction_item_modifier_link_modifier_id (modifier_id),
        CONSTRAINT fk_auction_item_modifier_link_item
            FOREIGN KEY (auction_item_id) REFERENCES auction_item (id),
        CONSTRAINT fk_auction_item_modifier_link_modifier
            FOREIGN KEY (modifier_id) REFERENCES auction_item_modifier (id)
    )',
    'SELECT 1'
);
PREPARE create_modifier_link_table_stmt FROM @create_modifier_link_table_sql;
EXECUTE create_modifier_link_table_stmt;
DEALLOCATE PREPARE create_modifier_link_table_stmt;

SET @migrate_legacy_modifier_links_sql = IF(
    @snapshot_tables_exist > 0 AND @legacy_modifier_link_exists > 0,
    'INSERT INTO auction_item_modifier_link (auction_item_id, sort_order, modifier_id)
     SELECT
         legacy.auction_item_id,
         ROW_NUMBER() OVER (PARTITION BY legacy.auction_item_id ORDER BY legacy.modifiers_id) - 1,
         legacy.modifiers_id
     FROM auction_item_modifiers legacy
     ON DUPLICATE KEY UPDATE
         modifier_id = VALUES(modifier_id)',
    'SELECT 1'
);
PREPARE migrate_legacy_modifier_links_stmt FROM @migrate_legacy_modifier_links_sql;
EXECUTE migrate_legacy_modifier_links_stmt;
DEALLOCATE PREPARE migrate_legacy_modifier_links_stmt;

DROP TABLE IF EXISTS auction_item_modifiers;

SET @populate_variant_hash_sql = IF(
    @snapshot_tables_exist > 0,
    'UPDATE auction_item item_row
     LEFT JOIN (
         SELECT
             link.auction_item_id,
             GROUP_CONCAT(
                 CONCAT(modifier_row.type, '':'', modifier_row.value)
                 ORDER BY modifier_row.type, modifier_row.value
                 SEPARATOR '',''
             ) AS modifier_key
         FROM auction_item_modifier_link link
         JOIN auction_item_modifier modifier_row ON modifier_row.id = link.modifier_id
         GROUP BY link.auction_item_id
     ) modifier_keys ON modifier_keys.auction_item_id = item_row.id
     SET item_row.variant_hash = LOWER(
         SHA2(
             CONCAT_WS(
                 ''|'',
                 item_row.item_id,
                 COALESCE(item_row.bonus_lists, ''''),
                 COALESCE(modifier_keys.modifier_key, ''''),
                 COALESCE(CAST(item_row.context AS CHAR), ''''),
                 COALESCE(CAST(item_row.pet_breed_id AS CHAR), ''''),
                 COALESCE(CAST(item_row.pet_level AS CHAR), ''''),
                 COALESCE(CAST(item_row.pet_quality_id AS CHAR), ''''),
                 COALESCE(CAST(item_row.pet_species_id AS CHAR), '''')
             ),
             256
         )
     )
     WHERE item_row.variant_hash IS NULL
        OR item_row.variant_hash = ''''',
    'SELECT 1'
);
PREPARE populate_variant_hash_stmt FROM @populate_variant_hash_sql;
EXECUTE populate_variant_hash_stmt;
DEALLOCATE PREPARE populate_variant_hash_stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_auction_item_canonical;
SET @create_tmp_auction_item_canonical_sql = IF(
    @snapshot_tables_exist > 0,
    'CREATE TEMPORARY TABLE tmp_auction_item_canonical AS
     SELECT MIN(id) AS canonical_id, variant_hash
     FROM auction_item
     GROUP BY variant_hash',
    'SELECT 1'
);
PREPARE create_tmp_auction_item_canonical_stmt FROM @create_tmp_auction_item_canonical_sql;
EXECUTE create_tmp_auction_item_canonical_stmt;
DEALLOCATE PREPARE create_tmp_auction_item_canonical_stmt;

SET @repoint_auction_items_sql = IF(
    @snapshot_tables_exist > 0,
    'UPDATE auction auction_row
     JOIN auction_item item_row ON item_row.id = auction_row.item_id
     JOIN tmp_auction_item_canonical canonical ON canonical.variant_hash = item_row.variant_hash
     SET auction_row.item_id = canonical.canonical_id
     WHERE auction_row.item_id <> canonical.canonical_id',
    'SELECT 1'
);
PREPARE repoint_auction_items_stmt FROM @repoint_auction_items_sql;
EXECUTE repoint_auction_items_stmt;
DEALLOCATE PREPARE repoint_auction_items_stmt;

SET @merge_modifier_links_sql = IF(
    @snapshot_tables_exist > 0,
    'INSERT INTO auction_item_modifier_link (auction_item_id, sort_order, modifier_id)
     SELECT
         canonical.canonical_id,
         duplicate_link.sort_order,
         duplicate_link.modifier_id
     FROM auction_item_modifier_link duplicate_link
     JOIN auction_item duplicate_item ON duplicate_item.id = duplicate_link.auction_item_id
     JOIN tmp_auction_item_canonical canonical ON canonical.variant_hash = duplicate_item.variant_hash
     WHERE duplicate_link.auction_item_id <> canonical.canonical_id
     ON DUPLICATE KEY UPDATE
         modifier_id = VALUES(modifier_id)',
    'SELECT 1'
);
PREPARE merge_modifier_links_stmt FROM @merge_modifier_links_sql;
EXECUTE merge_modifier_links_stmt;
DEALLOCATE PREPARE merge_modifier_links_stmt;

SET @delete_duplicate_modifier_links_sql = IF(
    @snapshot_tables_exist > 0,
    'DELETE duplicate_link
     FROM auction_item_modifier_link duplicate_link
     JOIN auction_item duplicate_item ON duplicate_item.id = duplicate_link.auction_item_id
     JOIN tmp_auction_item_canonical canonical ON canonical.variant_hash = duplicate_item.variant_hash
     WHERE duplicate_link.auction_item_id <> canonical.canonical_id',
    'SELECT 1'
);
PREPARE delete_duplicate_modifier_links_stmt FROM @delete_duplicate_modifier_links_sql;
EXECUTE delete_duplicate_modifier_links_stmt;
DEALLOCATE PREPARE delete_duplicate_modifier_links_stmt;

SET @delete_duplicate_items_sql = IF(
    @snapshot_tables_exist > 0,
    'DELETE duplicate_item
     FROM auction_item duplicate_item
     JOIN tmp_auction_item_canonical canonical ON canonical.variant_hash = duplicate_item.variant_hash
     WHERE duplicate_item.id <> canonical.canonical_id',
    'SELECT 1'
);
PREPARE delete_duplicate_items_stmt FROM @delete_duplicate_items_sql;
EXECUTE delete_duplicate_items_stmt;
DEALLOCATE PREPARE delete_duplicate_items_stmt;

SET @set_variant_hash_not_null_sql = IF(
    @snapshot_tables_exist > 0,
    'ALTER TABLE auction_item
        MODIFY COLUMN variant_hash CHAR(64) NOT NULL',
    'SELECT 1'
);
PREPARE set_variant_hash_not_null_stmt FROM @set_variant_hash_not_null_sql;
EXECUTE set_variant_hash_not_null_stmt;
DEALLOCATE PREPARE set_variant_hash_not_null_stmt;

SET @drop_auction_item_variant_hash_sql = IF(
    @snapshot_tables_exist > 0 AND EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auction_item'
          AND index_name = 'uk_auction_item_variant_hash'
    ),
    'DROP INDEX uk_auction_item_variant_hash ON auction_item',
    'SELECT 1'
);
PREPARE drop_auction_item_variant_hash_stmt FROM @drop_auction_item_variant_hash_sql;
EXECUTE drop_auction_item_variant_hash_stmt;
DEALLOCATE PREPARE drop_auction_item_variant_hash_stmt;

SET @drop_auction_item_item_id_sql = IF(
    @snapshot_tables_exist > 0 AND EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auction_item'
          AND index_name = 'idx_auction_item_item_id'
    ),
    'DROP INDEX idx_auction_item_item_id ON auction_item',
    'SELECT 1'
);
PREPARE drop_auction_item_item_id_stmt FROM @drop_auction_item_item_id_sql;
EXECUTE drop_auction_item_item_id_stmt;
DEALLOCATE PREPARE drop_auction_item_item_id_stmt;

SET @create_auction_item_variant_hash_sql = IF(
    @snapshot_tables_exist > 0,
    'CREATE UNIQUE INDEX uk_auction_item_variant_hash
        ON auction_item (variant_hash)',
    'SELECT 1'
);
PREPARE create_auction_item_variant_hash_stmt FROM @create_auction_item_variant_hash_sql;
EXECUTE create_auction_item_variant_hash_stmt;
DEALLOCATE PREPARE create_auction_item_variant_hash_stmt;

SET @create_auction_item_item_id_sql = IF(
    @snapshot_tables_exist > 0,
    'CREATE INDEX idx_auction_item_item_id
        ON auction_item (item_id)',
    'SELECT 1'
);
PREPARE create_auction_item_item_id_stmt FROM @create_auction_item_item_id_sql;
EXECUTE create_auction_item_item_id_stmt;
DEALLOCATE PREPARE create_auction_item_item_id_stmt;

SET @drop_auction_realm_update_deleted_sql = IF(
    @snapshot_tables_exist > 0 AND EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auction'
          AND index_name = 'idx_auction_connected_realm_update_deleted'
    ),
    'DROP INDEX idx_auction_connected_realm_update_deleted ON auction',
    'SELECT 1'
);
PREPARE drop_auction_realm_update_deleted_stmt FROM @drop_auction_realm_update_deleted_sql;
EXECUTE drop_auction_realm_update_deleted_stmt;
DEALLOCATE PREPARE drop_auction_realm_update_deleted_stmt;

SET @drop_auction_deleted_at_sql = IF(
    @snapshot_tables_exist > 0 AND EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auction'
          AND index_name = 'idx_auction_deleted_at'
    ),
    'DROP INDEX idx_auction_deleted_at ON auction',
    'SELECT 1'
);
PREPARE drop_auction_deleted_at_stmt FROM @drop_auction_deleted_at_sql;
EXECUTE drop_auction_deleted_at_stmt;
DEALLOCATE PREPARE drop_auction_deleted_at_stmt;

SET @create_auction_realm_update_deleted_sql = IF(
    @snapshot_tables_exist > 0,
    'CREATE INDEX idx_auction_connected_realm_update_deleted
        ON auction (connected_realm_id, update_history_id, deleted_at)',
    'SELECT 1'
);
PREPARE create_auction_realm_update_deleted_stmt FROM @create_auction_realm_update_deleted_sql;
EXECUTE create_auction_realm_update_deleted_stmt;
DEALLOCATE PREPARE create_auction_realm_update_deleted_stmt;

SET @create_auction_deleted_at_sql = IF(
    @snapshot_tables_exist > 0,
    'CREATE INDEX idx_auction_deleted_at
        ON auction (deleted_at)',
    'SELECT 1'
);
PREPARE create_auction_deleted_at_stmt FROM @create_auction_deleted_at_sql;
EXECUTE create_auction_deleted_at_stmt;
DEALLOCATE PREPARE create_auction_deleted_at_stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_auction_modifier_canonical;
DROP TEMPORARY TABLE IF EXISTS tmp_auction_item_canonical;
