ALTER TABLE `auction_stats_daily`
    PARTITION BY HASH (to_days(`date`))
        PARTITIONS 31;
