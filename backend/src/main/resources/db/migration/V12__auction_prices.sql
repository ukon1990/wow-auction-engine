-- DROP TABLE deprecated tables
DROP TABLE IF EXISTS auction_item_modifier_link;
DROP TABLE IF EXISTS auction_item_modifier;
DROP TABLE IF EXISTS auction;
DROP TABLE IF EXISTS auction_item;

-- Creating the new smaller focused tables
CREATE TABLE auction
(
    id                 VARCHAR(64) PRIMARY KEY NOT NULL,
    connected_realm_id INT NOT NULL,
    item_id            INT NOT NULL,
    pet_species_id     INT,
    pet_quality_id     INT,
    pet_level          TINYINT,
    modifier_key       VARCHAR(255) NOT NULL DEFAULT '',
    bonus_key          VARCHAR(255) NOT NULL DEFAULT '',
    buyout             BIGINT NOT NULL,
    bid                BIGINT NOT NULL,
    p25                BIGINT NOT NULL,
    p75                BIGINT NOT NULL,
    quantity           INT NOT NULL,
    first_seen         DATETIME,
    last_seen          DATETIME,
    update_history_id  BIGINT,
    CONSTRAINT auction_connected_realm_id_fk
        FOREIGN KEY (connected_realm_id) REFERENCES connected_realm (id),
    CONSTRAINT auction_update_history_id_fk
        FOREIGN KEY (update_history_id) REFERENCES auction_update_history (id),
    CONSTRAINT auction_unique_variant
        UNIQUE (
                connected_realm_id,
                item_id,
                pet_species_id,
                pet_quality_id,
                pet_level,
                modifier_key,
                bonus_key
        )
);

CREATE TABLE auction_price (
    id              BIGINT PRIMARY KEY, -- From blizzard's id
    auction_id      VARCHAR(64),
    buyout          BIGINT,
    bid             BIGINT,
    quantity        INT,
    last_modified   DATETIME,
    CONSTRAINT auction_price_auction_id_fk
        FOREIGN KEY (auction_id) REFERENCES auction (id)
);

CREATE INDEX auction_price_auction_id_idx ON auction_price (auction_id);
CREATE INDEX auction_price_last_modified_idx ON auction_price (last_modified);
