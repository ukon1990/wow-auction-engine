SET @copy_avg_delay_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'auction_house'
              AND column_name = 'average_delay'
        ) AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'auction_house'
              AND column_name = 'avg_delay'
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
        ) AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'auction_house'
              AND column_name = 'update_attempts'
        ),
        'UPDATE auction_house SET update_attempts = failed_attempts WHERE (update_attempts IS NULL OR update_attempts = 0) AND failed_attempts IS NOT NULL',
        'SELECT 1'
    )
);
PREPARE copy_update_attempts_stmt FROM @copy_update_attempts_sql;
EXECUTE copy_update_attempts_stmt;
DEALLOCATE PREPARE copy_update_attempts_stmt;

ALTER TABLE auction_house
    DROP COLUMN IF EXISTS average_delay,
    DROP COLUMN IF EXISTS failed_attempts,
    DROP COLUMN IF EXISTS realm_slugs,
    DROP COLUMN IF EXISTS realms_json;
