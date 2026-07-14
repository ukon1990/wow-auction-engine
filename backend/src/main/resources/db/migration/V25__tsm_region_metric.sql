ALTER TABLE auction_house
    ADD COLUMN last_tsm_region_sync datetime(6) DEFAULT NULL;

CREATE TABLE tsm_region_metric (
    region             enum('Europe','Korea','NorthAmerica','Taiwan') NOT NULL,
    subject_type       enum('ITEM','PET') NOT NULL,
    subject_id         int(11) NOT NULL,
    sale_rate          decimal(20, 8) NOT NULL,
    sold_per_day       decimal(20, 8) NOT NULL,
    market_value       bigint(20) DEFAULT NULL,
    historical         bigint(20) DEFAULT NULL,
    avg_sale_price     bigint(20) DEFAULT NULL,
    source_updated_at  datetime(6) NOT NULL,
    PRIMARY KEY (region, subject_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
