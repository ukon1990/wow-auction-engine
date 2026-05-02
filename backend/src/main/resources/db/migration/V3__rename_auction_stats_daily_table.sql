ALTER TABLE `daily_auction_stats` RENAME TO `auction_stats_daily`;

ALTER TABLE `auction_stats_daily`
PARTITION BY HASH (month(`date`))
PARTITIONS 12;
