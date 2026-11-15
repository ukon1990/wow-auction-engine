-- Covering index for the current-listing scans in auction and crafting search, so they no longer
-- look up every matching row in the clustered index for p25/p75/quantity.
-- It has the same leading columns as the index it replaces. INPLACE/LOCK=NONE keeps the table
-- readable and writable while the index builds.
ALTER TABLE auction
    ADD INDEX idx_auction_current_listing (
        update_history_id,
        connected_realm_id,
        item_id,
        buyout,
        bonus_key,
        modifier_key,
        pet_species_id,
        p25,
        p75,
        quantity
    ),
    DROP INDEX idx_auction_update_history_realm_item_buyout,
    ALGORITHM = INPLACE,
    LOCK = NONE;
