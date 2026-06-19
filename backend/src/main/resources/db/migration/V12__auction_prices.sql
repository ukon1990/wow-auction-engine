-- DROP TABLE deprecated tables
DROP TABLE IF EXISTS auction_item_modifier_link;
DROP TABLE IF EXISTS auction_item_modifier;
DROP TABLE IF EXISTS auction_price;
DROP TABLE IF EXISTS auction;
DROP TABLE IF EXISTS auction_item;


-- Creating the new smaller focused tables
CREATE TABLE auction
(
    id                 VARCHAR(70) PRIMARY KEY NOT NULL,
    connected_realm_id INT NOT NULL,
    item_id            INT NOT NULL,
    context            INT,
    pet_breed_id       INT,
    pet_species_id     INT,
    pet_quality_id     INT,
    pet_level          TINYINT,
    modifier_key       VARCHAR(255) NOT NULL DEFAULT '',
    bonus_key          VARCHAR(255) NOT NULL DEFAULT '',
    buyout             BIGINT NOT NULL,
    bid                BIGINT,
    p25                BIGINT NOT NULL,
    p75                BIGINT NOT NULL,
    quantity           INT NOT NULL,
    first_seen         DATETIME,
    last_seen          DATETIME,
    update_history_id  BIGINT,
    CONSTRAINT auction_connected_realm_id_fk
        FOREIGN KEY (connected_realm_id) REFERENCES connected_realm (id),
    CONSTRAINT auction_update_history_id_fk
        FOREIGN KEY (update_history_id) REFERENCES connected_realm_update_history (id),
    CONSTRAINT auction_unique_variant
        UNIQUE (
                connected_realm_id,
                item_id,
                context,
                pet_breed_id,
                pet_species_id,
                pet_quality_id,
                pet_level,
                modifier_key,
                bonus_key
        )
);

CREATE OR REPLACE INDEX idx_auction_update_history_realm_item_buyout
    ON auction (
        update_history_id,
        connected_realm_id,
        item_id,
        buyout,
        bonus_key,
        modifier_key,
        pet_species_id
    );

CREATE TABLE auction_price (
    id              BIGINT PRIMARY KEY, -- From blizzard's id
    auction_id      VARCHAR(70),
    buyout          BIGINT,
    bid             BIGINT,
    quantity        INT,
    last_modified   DATETIME,
    update_history_id  BIGINT,
    CONSTRAINT auction_price_auction_id_fk
        FOREIGN KEY (auction_id) REFERENCES auction (id),
    CONSTRAINT auction_price_update_history_id_fk
        FOREIGN KEY (update_history_id) REFERENCES connected_realm_update_history (id)
);

CREATE INDEX auction_price_auction_id_idx ON auction_price (auction_id);
CREATE INDEX auction_price_last_modified_idx ON auction_price (last_modified);
CREATE OR REPLACE INDEX idx_connected_realm_update_history_realm_id_id
    ON connected_realm_update_history (connected_realm_id, id);
