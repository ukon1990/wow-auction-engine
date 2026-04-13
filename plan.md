# Plan

## Context
- We want locale rows to be unique per owning entity/field, not globally deduplicated by text content.
- `locale_dbo` currently uses a generated numeric `id`, and several write paths still create new locale rows instead of reusing a stable owner-based key.
- The current risk is real: `profession`, `skill_tier`, and `recipe` already reuse locale rows via `name_id` / `description_id`, but child records such as recipe reagents, modified crafting slots/categories, metadata rows, item-related rows, and especially replaced collections can still create duplicate locale rows.
- This needs to work across all `LocaleDBO` owners (`profession`, `recipe`, `item`, metadata, item quality/inventory type/subclass/class, etc.).
- There is no production data yet, and this table is currently Hibernate-managed (`spring.jpa.hibernate.ddl-auto=update`), so we can plan a schema change without a data backfill migration.

## Approach
- **Recommended design:** keep the surrogate `id` as the primary key for simple foreign-key references, but add a stable business key on the locale row:
  - `source_type`
  - `source_key`
  - `source_field`
- Add a unique constraint/index on `(source_type, source_key, source_field)`.
- Use `source_key` as a **string**, not a numeric-only `source_id`, because not every locale owner has a single numeric id:
  - `item.id`, `recipe.id`, `profession.id` are numeric
  - `item_quality.type` / `inventory_type.type` are string identifiers
  - `item_subclass` is effectively composite (`classId` + `subclassId`)
- Refactor locale creation so every `LocaleDBO` is built with explicit owner metadata, for example:
  - `profession / 171 / name`
  - `profession / 171 / description`
  - `recipe / 34768 / name`
  - `item / 19019 / name`
  - `item_subclass / 2:7 / display_name`
- Update JDBC writes to upsert/find locale rows by `(source_type, source_key, source_field)` instead of only by generated `id`.
- Keep existing foreign-key columns like `name_id` / `description_id`; they will continue pointing to `locale_dbo.id`.
- Special case: `ProfessionCategory` does not have a stable external id in the current model. For locale ownership there are two realistic options, and the recommended one here is to treat it as lifecycle-owned data and delete its locale rows when categories are replaced, rather than pretending it has a stable source key that could drift.

## Files to modify
- `src/main/kotlin/net/jonasmf/auctionengine/dbo/rds/LocaleDBO.kt`
- `src/main/kotlin/net/jonasmf/auctionengine/mapper/LocaleMapper.kt`
- `src/main/kotlin/net/jonasmf/auctionengine/mapper/ProfessionMapper.kt`
- `src/main/kotlin/net/jonasmf/auctionengine/mapper/SkillTierMapper.kt`
- `src/main/kotlin/net/jonasmf/auctionengine/mapper/RecipeMapper.kt`
- `src/main/kotlin/net/jonasmf/auctionengine/mapper/ModifiedCraftingMapper.kt`
- `src/main/kotlin/net/jonasmf/auctionengine/mapper/ItemMapper.kt`
- `src/main/kotlin/net/jonasmf/auctionengine/repository/rds/ProfessionRecipeJdbcRepository.kt`
- `src/main/kotlin/net/jonasmf/auctionengine/service/ProfessionRecipeBulkSyncService.kt`
- Potentially any repository/service that persists `ItemDBO` or other JPA-cascaded `LocaleDBO` owners if source metadata must be attached before save.

## Reuse
- `ProfessionRecipeJdbcRepository.syncProfessionSkillTier(...)` already has the owner context needed to generate stable locale source keys for `profession`, `skill_tier`, `recipe`, `recipe_reagent`, and modified crafting slot/category rows.
- `ProfessionRecipeJdbcRepository.insertLocale(...)` / `updateLocale(...)` are the natural place to consolidate source-aware upsert logic for the JDBC sync path.
- `LocaleDTO.toDBO()` in `src/main/kotlin/net/jonasmf/auctionengine/mapper/LocaleMapper.kt` is the main reuse point for the JPA/cascade paths, but it must become context-aware because today it cannot attach any owner identity.
- Existing entity mappings already keep locale references in `name_id` / `description_id`-style columns, so adding a business key to `locale_dbo` does not require changing every foreign key to a composite key.

## Steps
- [ ] Extend `LocaleDBO` with source metadata (`sourceType`, `sourceKey`, `sourceField`) plus a unique constraint/index on that tuple while keeping `id` as the PK.
- [ ] Replace context-free locale mapping with source-aware locale factory/helper methods so each owner/field creates a deterministic locale identity.
- [ ] Update `ProfessionRecipeJdbcRepository` to find-or-upsert locale rows by source tuple and reuse the resulting `locale_dbo.id` in owner tables.
- [ ] Thread source metadata through all JPA mapper paths (`profession`, `skill tier`, `recipe`, modified crafting metadata, item/item-class/item-subclass/item-quality/inventory-type/item-summary) so `CascadeType.ALL` inserts do not generate duplicate locale rows.
- [ ] Handle owner types without a stable source id: explicitly delete owned locale rows when replacing `ProfessionCategory` rows, rather than leaving orphaned `locale_dbo` entries behind.
- [ ] Confirm schema generation works with the current Hibernate-managed table setup and document that no data migration/backfill is needed.

## Verification
- Run the same profession/recipe sync twice and confirm `locale_dbo` row counts stay stable for entities with stable source keys.
- Verify updates change existing locale content in place for the same `(source_type, source_key, source_field)` instead of inserting a second row.
- Verify replaced child collections do not leave orphaned locale rows, especially profession categories.
- Exercise at least one JPA/cascade path (for example item persistence and modified crafting metadata persistence) and confirm it reuses the expected locale identity.
- Run/update relevant profession/recipe sync tests and any item-persistence tests that cover `LocaleDBO` creation.
