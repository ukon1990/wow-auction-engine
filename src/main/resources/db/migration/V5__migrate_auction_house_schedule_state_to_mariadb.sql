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
        'UPDATE auction_house SET avg_delay = average_delay WHERE (avg_delay IS NULL OR avg_delay = 0) AND average_delay IS NOT NULL',
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
        'UPDATE auction_house SET update_attempts = failed_attempts WHERE (update_attempts IS NULL OR update_attempts = 0) AND failed_attempts IS NOT NULL',
        'SELECT 1'
    )
);
PREPARE copy_update_attempts_stmt FROM @copy_update_attempts_sql;
EXECUTE copy_update_attempts_stmt;
DEALLOCATE PREPARE copy_update_attempts_stmt;

UPDATE auction_house ah
JOIN connected_realm cr ON cr.auction_house_id = ah.id
SET ah.connected_id = cr.id
WHERE ah.connected_id IS NULL OR ah.connected_id <> cr.id;

UPDATE auction_house ah
LEFT JOIN connected_realm cr ON cr.auction_house_id = ah.id
SET ah.connected_id = ah.id
WHERE (ah.connected_id IS NULL OR ah.connected_id = 0)
  AND cr.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_auction_house_dedup;
CREATE TEMPORARY TABLE tmp_auction_house_dedup (
    duplicate_id INT NOT NULL PRIMARY KEY,
    keeper_id INT NOT NULL
);

INSERT INTO tmp_auction_house_dedup (duplicate_id, keeper_id)
SELECT ah.id AS duplicate_id,
       keeper.keeper_id
FROM auction_house ah
JOIN (
    SELECT grouped.connected_id,
           COALESCE(
               MIN(CASE WHEN cr.id IS NOT NULL THEN ah2.id END),
               MIN(ah2.id)
           ) AS keeper_id
    FROM auction_house ah2
    LEFT JOIN connected_realm cr ON cr.auction_house_id = ah2.id
    JOIN (
        SELECT connected_id
        FROM auction_house
        WHERE connected_id IS NOT NULL
        GROUP BY connected_id
        HAVING COUNT(*) > 1
    ) grouped ON grouped.connected_id = ah2.connected_id
    GROUP BY grouped.connected_id
) keeper ON keeper.connected_id = ah.connected_id
WHERE ah.id <> keeper.keeper_id;

UPDATE connected_realm cr
JOIN tmp_auction_house_dedup dedup ON dedup.duplicate_id = cr.auction_house_id
SET cr.auction_house_id = dedup.keeper_id;

SET @repoint_log_fk_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'auction_house_file_log'
              AND column_name = 'auction_house_id'
        ),
        'UPDATE auction_house_file_log log_entry
         JOIN tmp_auction_house_dedup dedup ON dedup.duplicate_id = log_entry.auction_house_id
         SET auction_house_id = dedup.keeper_id',
        'SELECT 1'
    )
);
PREPARE repoint_log_fk_stmt FROM @repoint_log_fk_sql;
EXECUTE repoint_log_fk_stmt;
DEALLOCATE PREPARE repoint_log_fk_stmt;

DELETE ah
FROM auction_house ah
JOIN tmp_auction_house_dedup dedup ON dedup.duplicate_id = ah.id;

DROP TEMPORARY TABLE IF EXISTS tmp_auction_house_dedup;

UPDATE auction_house ah
JOIN connected_realm cr ON cr.auction_house_id = ah.id
SET ah.connected_id = cr.id
WHERE ah.connected_id IS NULL OR ah.connected_id <> cr.id;

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

SET @add_log_fk_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'auction_house_file_log'
              AND constraint_type = 'FOREIGN KEY'
              AND constraint_name = 'fk_auction_house_file_log_auction_house'
        ),
        'SELECT 1',
        'ALTER TABLE auction_house_file_log
            ADD CONSTRAINT fk_auction_house_file_log_auction_house
                FOREIGN KEY (auction_house_id) REFERENCES auction_house (id)'
    )
);
PREPARE add_log_fk_stmt FROM @add_log_fk_sql;
EXECUTE add_log_fk_stmt;
DEALLOCATE PREPARE add_log_fk_stmt;
