# Auction Snapshot Storage and Querying

This project keeps a compact "current snapshot" of auctions in MariaDB alongside the aggregated hourly price statistics.

The snapshot schema is intentionally optimized for heavy hourly upserts first, while still supporting targeted read paths for browsing the latest active auctions.

## Data model

### `auction`

One row per live-or-recently-seen auction identity within a connected realm.

Important columns:

- `connected_realm_id`: realm partition key
- `id`: Blizzard auction ID inside the connected realm
- `item_id`: foreign key to `auction_item.id` (this is **not** the base Blizzard item ID)
- `first_seen`: first snapshot timestamp where this auction was observed
- `last_seen`: most recent snapshot timestamp where this auction was observed
- `deleted_at`: soft-delete timestamp when an auction disappeared from the latest snapshot
- `update_history_id`: snapshot batch that last touched the row

Primary key:

- `(connected_realm_id, id)`

### `auction_item`

Canonical item-variant row shared by many auctions.

Important columns:

- `id`: surrogate key referenced by `auction.item_id`
- `item_id`: base Blizzard item ID
- `variant_hash`: canonical hash of item ID + bonuses + modifiers + context + pet metadata

### `auction_item_modifier` and `auction_item_modifier_link`

Normalized modifier storage for item variants.

## Why `auction.item_id` is not base item ID

`auction.item_id` references `auction_item.id`, because many auctions can share the same canonical item variant.

That means "find auctions for connected realm X and base item Y" is a join problem:

```sql
SELECT a.*
FROM auction a
JOIN auction_item ai ON ai.id = a.item_id
WHERE a.connected_realm_id = ?
  AND ai.item_id = ?;
```

This design avoids copying variant metadata into every auction row and keeps snapshot upserts smaller.

## Snapshot-oriented indexes

Current important indexes for the snapshot tables:

### `auction`

- primary key: `(connected_realm_id, id)`
- `idx_auction_connected_realm_update_deleted (connected_realm_id, update_history_id, deleted_at)`
  - supports snapshot completion and soft-delete marking paths
- `idx_auction_deleted_at (deleted_at)`
  - supports cleanup of aged soft-deleted rows
- `idx_auction_item_realm_deleted_last_seen (item_id, connected_realm_id, deleted_at, last_seen)`
  - supports read-side lookup of active auctions by item variant within a realm, ordered/filterable by recency

### `auction_item`

- unique `uk_auction_item_variant_hash (variant_hash)`
  - supports canonical variant lookup/upsert
- `idx_auction_item_item_id (item_id, id)`
  - supports base-item to variant-ID expansion before joining into `auction`

## Recommended read query shape

For current active-auction reads by base item and connected realm, prefer this shape:

```sql
SELECT a.id,
       a.quantity,
       a.unit_price,
       a.buyout,
       a.time_left,
       a.first_seen,
       a.last_seen
FROM auction a
JOIN auction_item ai ON ai.id = a.item_id
WHERE ai.item_id = :itemId
  AND a.connected_realm_id = :connectedRealmId
  AND a.deleted_at IS NULL
  AND a.last_seen >= :cutoff
ORDER BY a.last_seen DESC
LIMIT :limit;
```

Why this shape works well:

1. `auction_item(item_id, id)` narrows the variant IDs for the base item.
2. `auction(item_id, connected_realm_id, deleted_at, last_seen)` probes the matching auction rows efficiently.
3. `deleted_at IS NULL` keeps the query on the active snapshot.

## Tradeoff note

We intentionally keep the number of `auction` indexes low.

This table is write-heavy during hourly snapshot ingestion, so every added index increases insert/upsert cost. The current index set is meant to cover:

- snapshot persistence
- soft-delete marking
- aged-row cleanup
- targeted current-auction reads by realm + item + recency

If a new query pattern becomes hot, check `EXPLAIN` against production-like data before adding another index.
