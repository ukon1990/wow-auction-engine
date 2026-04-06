CREATE OR REPLACE VIEW v_auction_house_prices AS
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       0 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(0, 0, 0)) AS auction_timestamp,
       price00 AS price,
       quantity00 AS quantity
FROM hourly_auction_stats
WHERE price00 IS NOT NULL OR quantity00 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       1 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(1, 0, 0)) AS auction_timestamp,
       price01 AS price,
       quantity01 AS quantity
FROM hourly_auction_stats
WHERE price01 IS NOT NULL OR quantity01 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       2 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(2, 0, 0)) AS auction_timestamp,
       price02 AS price,
       quantity02 AS quantity
FROM hourly_auction_stats
WHERE price02 IS NOT NULL OR quantity02 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       3 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(3, 0, 0)) AS auction_timestamp,
       price03 AS price,
       quantity03 AS quantity
FROM hourly_auction_stats
WHERE price03 IS NOT NULL OR quantity03 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       4 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(4, 0, 0)) AS auction_timestamp,
       price04 AS price,
       quantity04 AS quantity
FROM hourly_auction_stats
WHERE price04 IS NOT NULL OR quantity04 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       5 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(5, 0, 0)) AS auction_timestamp,
       price05 AS price,
       quantity05 AS quantity
FROM hourly_auction_stats
WHERE price05 IS NOT NULL OR quantity05 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       6 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(6, 0, 0)) AS auction_timestamp,
       price06 AS price,
       quantity06 AS quantity
FROM hourly_auction_stats
WHERE price06 IS NOT NULL OR quantity06 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       7 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(7, 0, 0)) AS auction_timestamp,
       price07 AS price,
       quantity07 AS quantity
FROM hourly_auction_stats
WHERE price07 IS NOT NULL OR quantity07 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       8 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(8, 0, 0)) AS auction_timestamp,
       price08 AS price,
       quantity08 AS quantity
FROM hourly_auction_stats
WHERE price08 IS NOT NULL OR quantity08 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       9 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(9, 0, 0)) AS auction_timestamp,
       price09 AS price,
       quantity09 AS quantity
FROM hourly_auction_stats
WHERE price09 IS NOT NULL OR quantity09 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       10 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(10, 0, 0)) AS auction_timestamp,
       price10 AS price,
       quantity10 AS quantity
FROM hourly_auction_stats
WHERE price10 IS NOT NULL OR quantity10 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       11 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(11, 0, 0)) AS auction_timestamp,
       price11 AS price,
       quantity11 AS quantity
FROM hourly_auction_stats
WHERE price11 IS NOT NULL OR quantity11 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       12 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(12, 0, 0)) AS auction_timestamp,
       price12 AS price,
       quantity12 AS quantity
FROM hourly_auction_stats
WHERE price12 IS NOT NULL OR quantity12 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       13 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(13, 0, 0)) AS auction_timestamp,
       price13 AS price,
       quantity13 AS quantity
FROM hourly_auction_stats
WHERE price13 IS NOT NULL OR quantity13 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       14 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(14, 0, 0)) AS auction_timestamp,
       price14 AS price,
       quantity14 AS quantity
FROM hourly_auction_stats
WHERE price14 IS NOT NULL OR quantity14 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       15 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(15, 0, 0)) AS auction_timestamp,
       price15 AS price,
       quantity15 AS quantity
FROM hourly_auction_stats
WHERE price15 IS NOT NULL OR quantity15 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       16 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(16, 0, 0)) AS auction_timestamp,
       price16 AS price,
       quantity16 AS quantity
FROM hourly_auction_stats
WHERE price16 IS NOT NULL OR quantity16 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       17 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(17, 0, 0)) AS auction_timestamp,
       price17 AS price,
       quantity17 AS quantity
FROM hourly_auction_stats
WHERE price17 IS NOT NULL OR quantity17 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       18 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(18, 0, 0)) AS auction_timestamp,
       price18 AS price,
       quantity18 AS quantity
FROM hourly_auction_stats
WHERE price18 IS NOT NULL OR quantity18 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       19 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(19, 0, 0)) AS auction_timestamp,
       price19 AS price,
       quantity19 AS quantity
FROM hourly_auction_stats
WHERE price19 IS NOT NULL OR quantity19 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       20 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(20, 0, 0)) AS auction_timestamp,
       price20 AS price,
       quantity20 AS quantity
FROM hourly_auction_stats
WHERE price20 IS NOT NULL OR quantity20 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       21 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(21, 0, 0)) AS auction_timestamp,
       price21 AS price,
       quantity21 AS quantity
FROM hourly_auction_stats
WHERE price21 IS NOT NULL OR quantity21 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       22 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(22, 0, 0)) AS auction_timestamp,
       price22 AS price,
       quantity22 AS quantity
FROM hourly_auction_stats
WHERE price22 IS NOT NULL OR quantity22 IS NOT NULL
UNION ALL
SELECT connected_realm_id,
       date,
       ah_type_id,
       item_id,
       pet_species_id,
       23 AS hour_of_day,
       TIMESTAMP(date, MAKETIME(23, 0, 0)) AS auction_timestamp,
       price23 AS price,
       quantity23 AS quantity
FROM hourly_auction_stats
WHERE price23 IS NOT NULL OR quantity23 IS NOT NULL;
