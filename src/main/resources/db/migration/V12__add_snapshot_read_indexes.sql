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

SET @drop_auction_item_item_id_sql = IF(
    @auction_item_exists > 0 AND EXISTS (
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

SET @create_auction_item_item_id_sql = IF(
    @auction_item_exists > 0,
    'CREATE INDEX idx_auction_item_item_id
        ON auction_item (item_id, id)',
    'SELECT 1'
);
PREPARE create_auction_item_item_id_stmt FROM @create_auction_item_item_id_sql;
EXECUTE create_auction_item_item_id_stmt;
DEALLOCATE PREPARE create_auction_item_item_id_stmt;

SET @create_auction_realm_item_deleted_last_seen_sql = IF(
    @auction_exists > 0 AND NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auction'
          AND index_name = 'idx_auction_realm_item_deleted_last_seen'
    ),
    'CREATE INDEX idx_auction_realm_item_deleted_last_seen
        ON auction (connected_realm_id, item_id, deleted_at, last_seen)',
    'SELECT 1'
);
PREPARE create_auction_realm_item_deleted_last_seen_stmt FROM @create_auction_realm_item_deleted_last_seen_sql;
EXECUTE create_auction_realm_item_deleted_last_seen_stmt;
DEALLOCATE PREPARE create_auction_realm_item_deleted_last_seen_stmt;
