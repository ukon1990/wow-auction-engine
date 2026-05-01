-- This primary key does improve the query index/pk so that it better matches how we query the data.
ALTER TABLE auction_stats_hourly
DROP PRIMARY KEY,
ADD PRIMARY KEY (
    connected_realm_id,
    `date`,
    item_id,
    pet_species_id,
    modifier_key,
    bonus_key
);
