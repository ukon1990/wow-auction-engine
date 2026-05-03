CREATE INDEX IF NOT EXISTS idx_realm_region_slug ON realm (region_id, slug);

CREATE INDEX IF NOT EXISTS idx_recipe_crafted_item_id ON recipe (crafted_item_id);
