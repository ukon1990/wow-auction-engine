CREATE TABLE `blizzard_media_fetch_failure`
(
    `entity_kind`        varchar(32)  NOT NULL,
    `entity_id`          int          NOT NULL,
    `failure_count`      int          NOT NULL DEFAULT 0,
    `last_error_status`  varchar(32)           DEFAULT NULL,
    `last_error_message` varchar(512)          DEFAULT NULL,
    `last_failed_at`     datetime(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `next_retry_at`      datetime(6)           DEFAULT NULL,
    `manual_disabled`    bit(1)       NOT NULL DEFAULT b'0',
    `created_at`         datetime(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`         datetime(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`entity_kind`, `entity_id`),
    KEY `idx_blizzard_media_fetch_failure_retry` (`entity_kind`, `manual_disabled`, `next_retry_at`),
    KEY `idx_blizzard_media_fetch_failure_next_retry` (`next_retry_at`)
);
