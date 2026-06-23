CREATE TABLE expansion (
    id            INT          NOT NULL,
    slug          VARCHAR(64)  NOT NULL,
    name_id       BIGINT       NOT NULL,
    major_version INT          NOT NULL,
    display_order INT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_expansion_slug (slug),
    UNIQUE KEY uk_expansion_major_version (major_version),
    CONSTRAINT fk_expansion_name
        FOREIGN KEY (name_id) REFERENCES locale (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE expansion_item_range (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    expansion_id  INT          NOT NULL,
    start_item_id INT          NOT NULL,
    end_item_id   INT          NOT NULL,
    source        VARCHAR(32)  NOT NULL DEFAULT 'manual',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    note          VARCHAR(512) DEFAULT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_expansion_item_range_enabled_bounds (enabled, start_item_id, end_item_id),
    KEY idx_expansion_item_range_expansion_id (expansion_id),
    CONSTRAINT fk_expansion_item_range_expansion
        FOREIGN KEY (expansion_id) REFERENCES expansion (id),
    CONSTRAINT chk_expansion_item_range_bounds
        CHECK (start_item_id <= end_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `item`
    ADD COLUMN expansion_id INT DEFAULT NULL,
    ADD KEY idx_item_expansion_id (expansion_id),
    ADD CONSTRAINT fk_item_expansion
        FOREIGN KEY (expansion_id) REFERENCES expansion (id);

CREATE TABLE admin_item_job (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    type          VARCHAR(64)  NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    requested_by  VARCHAR(255) DEFAULT NULL,
    started_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at   TIMESTAMP    NULL DEFAULT NULL,
    summary_json  JSON         DEFAULT NULL,
    error_message VARCHAR(1024) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_admin_item_job_type_status (type, status),
    KEY idx_admin_item_job_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
