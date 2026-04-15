SET @auction_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
);

SET @auction_item_fk_name = (
    SELECT MIN(constraint_name)
    FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
      AND column_name = 'item_id'
      AND referenced_table_name = 'auction_item'
      AND referenced_column_name = 'id'
);

SET @accidental_auction_item_unique_index = (
    SELECT MIN(index_name)
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auction'
          AND non_unique = 0
          AND index_name <> 'PRIMARY'
        GROUP BY index_name
        HAVING COUNT(*) = 1
           AND MIN(column_name) = 'item_id'
           AND MAX(column_name) = 'item_id'
    ) accidental_indexes
);

SET @drop_auction_item_fk_sql = IF(
    @auction_exists > 0
    AND @auction_item_fk_name IS NOT NULL
    AND @accidental_auction_item_unique_index IS NOT NULL,
    CONCAT(
        'ALTER TABLE auction DROP FOREIGN KEY `',
        REPLACE(@auction_item_fk_name, '`', '``'),
        '`'
    ),
    'SELECT 1'
);
PREPARE drop_auction_item_fk_stmt FROM @drop_auction_item_fk_sql;
EXECUTE drop_auction_item_fk_stmt;
DEALLOCATE PREPARE drop_auction_item_fk_stmt;

SET @drop_accidental_auction_item_unique_index_sql = IF(
    @auction_exists > 0 AND @accidental_auction_item_unique_index IS NOT NULL,
    CONCAT(
        'DROP INDEX `',
        REPLACE(@accidental_auction_item_unique_index, '`', '``'),
        '` ON auction'
    ),
    'SELECT 1'
);
PREPARE drop_accidental_auction_item_unique_index_stmt FROM @drop_accidental_auction_item_unique_index_sql;
EXECUTE drop_accidental_auction_item_unique_index_stmt;
DEALLOCATE PREPARE drop_accidental_auction_item_unique_index_stmt;

SET @drop_old_snapshot_read_index_sql = IF(
    @auction_exists > 0 AND EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auction'
          AND index_name = 'idx_auction_realm_item_deleted_last_seen'
    ),
    'DROP INDEX idx_auction_realm_item_deleted_last_seen ON auction',
    'SELECT 1'
);
PREPARE drop_old_snapshot_read_index_stmt FROM @drop_old_snapshot_read_index_sql;
EXECUTE drop_old_snapshot_read_index_stmt;
DEALLOCATE PREPARE drop_old_snapshot_read_index_stmt;

SET @create_target_snapshot_read_index_sql = IF(
    @auction_exists > 0 AND NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auction'
          AND index_name = 'idx_auction_item_realm_deleted_last_seen'
    ),
    'CREATE INDEX idx_auction_item_realm_deleted_last_seen
        ON auction (item_id, connected_realm_id, deleted_at, last_seen)',
    'SELECT 1'
);
PREPARE create_target_snapshot_read_index_stmt FROM @create_target_snapshot_read_index_sql;
EXECUTE create_target_snapshot_read_index_stmt;
DEALLOCATE PREPARE create_target_snapshot_read_index_stmt;

SET @recreate_auction_item_fk_sql = IF(
    @auction_exists > 0
    AND @auction_item_fk_name IS NOT NULL
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'auction'
          AND column_name = 'item_id'
          AND referenced_table_name = 'auction_item'
          AND referenced_column_name = 'id'
    ),
    CONCAT(
        'ALTER TABLE auction ADD CONSTRAINT `',
        REPLACE(@auction_item_fk_name, '`', '``'),
        '` FOREIGN KEY (item_id) REFERENCES auction_item (id)'
    ),
    'SELECT 1'
);
PREPARE recreate_auction_item_fk_stmt FROM @recreate_auction_item_fk_sql;
EXECUTE recreate_auction_item_fk_stmt;
DEALLOCATE PREPARE recreate_auction_item_fk_stmt;
