CREATE INDEX IF NOT EXISTS idx_auction_stats_hourly_realm_date
    ON auction_stats_hourly (connected_realm_id, date);

CREATE INDEX IF NOT EXISTS idx_auction_stats_daily_realm_date
    ON auction_stats_daily (connected_realm_id, date);

CREATE INDEX IF NOT EXISTS idx_auction_price_last_modified_auction
    ON auction_price (last_modified, auction_id);
