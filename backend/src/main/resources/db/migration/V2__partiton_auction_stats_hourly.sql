ALTER TABLE `auction_stats_hourly`
    PARTITION BY HASH (to_days(`date`))
        PARTITIONS 31;
