ALTER TABLE file_reference
    ADD COLUMN IF NOT EXISTS size DOUBLE NOT NULL DEFAULT 0;

ALTER TABLE auction_house
    ADD COLUMN IF NOT EXISTS connected_id INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS region VARCHAR(32) NOT NULL DEFAULT 'Europe',
    ADD COLUMN IF NOT EXISTS auto_update BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN IF NOT EXISTS avg_delay BIGINT NULL,
    ADD COLUMN IF NOT EXISTS game_build INT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_daily_price_update DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS last_history_delete_event DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS last_history_delete_event_daily DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS last_stats_insert DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS last_trend_update_initiation DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS realm_slugs VARCHAR(1024) NULL,
    ADD COLUMN IF NOT EXISTS stats_last_modified BIGINT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS update_attempts INT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS realms_json LONGTEXT NULL;

SET @copy_avg_delay_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'auction_house'
              AND column_name = 'average_delay'
        ),
        'UPDATE auction_house SET avg_delay = COALESCE(avg_delay, average_delay)',
        'SELECT 1'
    )
);
PREPARE copy_avg_delay_stmt FROM @copy_avg_delay_sql;
EXECUTE copy_avg_delay_stmt;
DEALLOCATE PREPARE copy_avg_delay_stmt;

SET @copy_update_attempts_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'auction_house'
              AND column_name = 'failed_attempts'
        ),
        'UPDATE auction_house SET update_attempts = COALESCE(update_attempts, failed_attempts)',
        'SELECT 1'
    )
);
PREPARE copy_update_attempts_stmt FROM @copy_update_attempts_sql;
EXECUTE copy_update_attempts_stmt;
DEALLOCATE PREPARE copy_update_attempts_stmt;

UPDATE auction_house ah
JOIN connected_realm cr ON cr.auction_house_id = ah.id
SET ah.connected_id = cr.id
WHERE ah.connected_id = 0;

UPDATE auction_house ah
JOIN connected_realm cr ON cr.auction_house_id = ah.id
SET ah.region = COALESCE(NULLIF(ah.region, ''), 'Europe')
WHERE ah.region IS NULL OR ah.region = '';

ALTER TABLE auction_house_file_log
    ADD COLUMN IF NOT EXISTS last_modified DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS time_since_previous_dump BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS auction_house_id INT NULL;

SET @copy_log_timestamp_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'auction_house_file_log'
              AND column_name = 'timestamp'
        ),
        'UPDATE auction_house_file_log SET last_modified = COALESCE(last_modified, `timestamp`)',
        'SELECT 1'
    )
);
PREPARE copy_log_timestamp_stmt FROM @copy_log_timestamp_sql;
EXECUTE copy_log_timestamp_stmt;
DEALLOCATE PREPARE copy_log_timestamp_stmt;

SET @backfill_log_fk_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 'auction_house_update_log'
        ),
        'UPDATE auction_house_file_log l
         JOIN auction_house_update_log j ON j.update_log_id = l.id
         SET l.auction_house_id = COALESCE(l.auction_house_id, j.auction_house_id)',
        'SELECT 1'
    )
);
PREPARE backfill_log_fk_stmt FROM @backfill_log_fk_sql;
EXECUTE backfill_log_fk_stmt;
DEALLOCATE PREPARE backfill_log_fk_stmt;

ALTER TABLE auction_house
    ADD UNIQUE INDEX IF NOT EXISTS ux_auction_house_connected_id (connected_id),
    ADD INDEX IF NOT EXISTS ix_auction_house_region_next_update (region, next_update),
    ADD INDEX IF NOT EXISTS ix_auction_house_region (region);

ALTER TABLE auction_house_file_log
    ADD INDEX IF NOT EXISTS ix_auction_house_file_log_house_last_modified (auction_house_id, last_modified DESC),
    ADD UNIQUE INDEX IF NOT EXISTS ux_auction_house_file_log_house_last_modified (auction_house_id, last_modified);

ALTER TABLE auction_house_file_log
    ADD CONSTRAINT fk_auction_house_file_log_auction_house
        FOREIGN KEY (auction_house_id) REFERENCES auction_house (id);
