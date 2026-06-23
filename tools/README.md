# Auction JSON Inspection Scripts

## `map-auction-json.mjs`

Downloads the provided WoW auction JSON archives, stores the original `.json.gz` files, decompresses them into `.json`, and writes structure summaries for each payload:

- `structure.json`: hierarchical schema-like summary
- `structure-paths.txt`: flattened `$.path` view of the discovered keys and value types
- `enum-candidates.json`: low-cardinality primitive fields that look like enum candidates
- `enum-candidates.txt`: flattened human-readable enum candidate summary

By default, outputs are written under `target/auction-json-map/`, which is already ignored by Git.
By default, every element in each JSON array is inspected so the structure map is as complete as possible.
Primitive fields with `20` or fewer unique values are flagged as enum candidates.
Authenticated endpoints are supported via `--bearer-token`.

## `analyze-auction-field.mjs`

Downloads one or more WoW auction payloads, extracts a field from each auction entry, and writes value-distribution summaries. It is designed for fields such as `item.bonus_lists` where you want both per-value counts and per-array combination counts.

Outputs are written under `target/auction-field-analysis/` by default:

- `field-analysis.json`: structured per-source summary
- `field-analysis.txt`: readable per-source summary
- `summary.json`: aggregate results across all URLs
- `summary.txt`: readable aggregate summary

## `generate-expansion-ranges.mjs`

Parses ItemVersion `Data.lua`, derives an expansion from each item version's major client version, compresses item ids into maximal ranges (bridging gaps when no intervening item maps to a different expansion), and writes the repeatable Flyway seed for `expansion_item_range`.

Default update:

```powershell
node .\tools\generate-expansion-ranges.mjs
```

Offline update from a downloaded `Data.lua`:

```powershell
node .\tools\generate-expansion-ranges.mjs --input .\Data.lua
```

Tests:

```powershell
node --test .\tools\generate-expansion-ranges.test.mjs
```

## Usage

```powershell
node .\tools\map-auction-json.mjs
```

Custom URLs, output folder, a capped array sample size, and a custom enum threshold:

```powershell
node .\tools\map-auction-json.mjs `
  --url "https://wah-data-eu.s3.eu-north-1.amazonaws.com/engine/auctions/europe/commodity/1773733732000.json.gz" `
  --url "https://wah-data-eu.s3.eu-north-1.amazonaws.com/engine/auctions/europe/1403/1773733732000.json.gz" `
  --bearer-token "<token>" `
  --out-dir .\target\auction-json-map `
  --sample-size 250 `
  --enum-threshold 12
```

To explicitly inspect every array element, you can also pass:

```powershell
node .\tools\map-auction-json.mjs --sample-size all
```

## Test

```powershell
node --test .\tools\map-auction-json.test.mjs
```

Refresh-fixtures tests:

```powershell
node --test .\tools\refresh-fixtures.test.mjs
```

Field analysis for `item.bonus_lists` on authenticated Blizzard API endpoints:

```powershell
node .\tools\analyze-auction-field.mjs `
  --url "https://eu.api.blizzard.com/data/wow/connected-realm/1597/auctions?namespace=dynamic-eu&locale=en_US" `
  --url "https://eu.api.blizzard.com/data/wow/connected-realm/1598/auctions?namespace=dynamic-eu&locale=en_US" `
  --bearer-token "<token>" `
  --field-path "item.bonus_lists"
```

Tests:

```powershell
node --test .\tools\analyze-auction-field.test.mjs
```

## `refresh-fixtures.mjs`

Fetches and refreshes profession/skill-tier/recipe fixture data for test resources using Blizzard Game Data APIs.

[`refresh-fixtures.mjs`](refresh-fixtures.mjs) is a thin entrypoint; implementation lives under [`refresh-fixtures/`](refresh-fixtures/):

| Area | Module |
|------|--------|
| CLI / help | `refresh-fixtures/cli.mjs` |
| Env defaults, default profession list | `refresh-fixtures/config.mjs` |
| Fixture paths | `refresh-fixtures/paths.mjs` |
| Blizzard HTTP client | `refresh-fixtures/api/blizzard-client.mjs` |
| Link discovery / recursion | `refresh-fixtures/discovery/` |
| Tier & recipe sampling | `refresh-fixtures/sampling/` |
| Plan writes, prune, apply | `refresh-fixtures/plan/` |
| Resource batches | `refresh-fixtures/resources/` |

The refresher is config-driven internally and discovers dependent resources recursively from Blizzard `key.href` links. Today it manages filtered profession roots, sampled skill tiers, sampled recipes, and any non-media linked resources they reference, such as items, item classes, item appearances, and modified crafting metadata.

### Adding a resource batch

1. Add selection defaults (if any) under `ROOT_SELECTIONS` in [`refresh-fixtures/config.mjs`](refresh-fixtures/config.mjs).
2. Implement `buildPlan` in e.g. `refresh-fixtures/resources/<name>/plan.mjs` (see [`resources/profession/`](refresh-fixtures/resources/profession/)).
3. Register the resource in [`refresh-fixtures/resources/registry.mjs`](refresh-fixtures/resources/registry.mjs).
4. Extend [`refresh-fixtures/cli.mjs`](refresh-fixtures/cli.mjs) with flags such as `--<name>-id` when needed.
5. Re-export any symbols tests need from [`refresh-fixtures/index.mjs`](refresh-fixtures/index.mjs) and the shim [`refresh-fixtures.mjs`](refresh-fixtures.mjs).

By default it updates:

- `backend/src/test/resources/blizzard/profession/index-response.json`
- `backend/src/test/resources/blizzard/profession/<professionId>-response.json`
- `backend/src/test/resources/blizzard/profession/<professionId>/skill-tier/<skillTierId>-response.json`
- `backend/src/test/resources/blizzard/recipe/<recipeId>-response.json`
- `backend/src/test/resources/blizzard/item/<itemId>-response.json`
- `backend/src/test/resources/blizzard/modified-crafting/reagent-slot-type/<slotTypeId>-response.json`
- `backend/src/test/resources/blizzard/profession-recipe-sample-manifest.json`

The on-disk layout mirrors the normalized Blizzard API path under `backend/src/test/resources/blizzard`. Media links are intentionally excluded, and broken child links discovered during recursion are skipped instead of failing the whole refresh.

The refresh also writes a small set of deterministic supplemental test fixtures that are not fetched from Blizzard, such as client-error payloads and focused index responses used by isolated API client tests.

### Authentication

Uses the same app environment variables for Blizzard OAuth:

- `BLIZZARD_CLIENT_ID`
- `BLIZZARD_CLIENT_SECRET`

Optional overrides:

- `BLIZZARD_ACCESS_TOKEN` (use an existing bearer token instead of OAuth refresh)
- `BLIZZARD_TOKEN_URL` (default: `https://eu.battle.net/oauth/token`)
- `BLIZZARD_BASE_URL` (default: `https://us.api.blizzard.com/data/wow`)
- `BLIZZARD_NAMESPACE` (default: `static-us`)
- `BLIZZARD_LOCALE` (default: `en_US`)

### Usage

Default refresh (sample size defaults to the checked-in root selection config and can be overridden with `--sample-size`):

```powershell
node .\tools\refresh-fixtures.mjs
```

Project-root Maven entry point:

```powershell
cd backend; .\mvnw exec:exec@refresh-fixtures
```

Dry run:

```powershell
node .\tools\refresh-fixtures.mjs --dry-run
```

Dry run through Maven:

```powershell
cd backend; .\mvnw exec:exec@refresh-fixtures '-Drefresh.fixtures.args=--dry-run'
```

Refresh only selected professions:

```powershell
node .\tools\refresh-fixtures.mjs --profession-id 333,164 --sample-size 8
```

With `--profession-id`, the default is the same sampling as the built-in profession list: the two highest skill tiers and up to `--sample-size` recipes per tier (default 6). Use `--skill-tier-id` to pick tiers, `--full` to download every tier and recipe (e.g. Jewelcrafting `755` is 1400+ recipes and can take many minutes), or `--quiet` to hide progress on stderr.

Refresh only selected professions through Maven:

```powershell
cd backend; .\mvnw exec:exec@refresh-fixtures '-Drefresh.fixtures.args=--profession-id 333,164 --sample-size 8'
```

The Maven entry point still requires a local `node` executable on `PATH`. Override it with `-Dnode.executable=<path-to-node>` if needed.
