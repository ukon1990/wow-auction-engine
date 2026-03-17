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

## Usage

```powershell
node .\tools\map-auction-json.mjs
```

Custom URLs, output folder, a capped array sample size, and a custom enum threshold:

```powershell
node .\tools\map-auction-json.mjs `
  --url "https://wah-data-eu.s3.eu-west-1.amazonaws.com/engine/auctions/europe/commodity/1773733732000.json.gz" `
  --url "https://wah-data-eu.s3.eu-west-1.amazonaws.com/engine/auctions/europe/1403/1773733732000.json.gz" `
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



