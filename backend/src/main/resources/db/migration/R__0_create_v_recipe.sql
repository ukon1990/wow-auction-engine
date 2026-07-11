CREATE OR REPLACE VIEW v_recipe AS
SELECT
    recipe_ids.id,
    override_recipe.id IS NOT NULL AS is_override,
    COALESCE(override_recipe.crafted_item_id, base_recipe.crafted_item_id) AS crafted_item_id,
    COALESCE(override_recipe.crafted_quantity, base_recipe.crafted_quantity) AS crafted_quantity,
    COALESCE(override_recipe.last_modified, base_recipe.last_modified) AS last_modified,
    COALESCE(override_recipe.media_url, base_recipe.media_url) AS media_url,
    COALESCE(override_recipe.media_source_url, base_recipe.media_source_url) AS media_source_url,
    COALESCE(override_recipe.rank, base_recipe.rank) AS rank,
    COALESCE(override_recipe.required_skill_level, base_recipe.required_skill_level) AS required_skill_level,
    COALESCE(override_recipe.description_id, base_recipe.description_id) AS description_id,
    COALESCE(override_recipe.name_id, base_recipe.name_id) AS name_id,
    COALESCE(override_recipe.profession_category_id, base_recipe.profession_category_id) AS profession_category_id,
    override_recipe.override_note,
    COALESCE(override_recipe.created_at, base_recipe.created_at) AS created_at,
    COALESCE(override_recipe.updated_at, base_recipe.updated_at) AS updated_at
FROM (
    SELECT id
    FROM recipe
    WHERE is_override = FALSE
) recipe_ids
    LEFT JOIN recipe base_recipe
        ON base_recipe.id = recipe_ids.id
        AND base_recipe.is_override = FALSE
    LEFT JOIN recipe override_recipe
        ON override_recipe.id = recipe_ids.id
        AND override_recipe.is_override = TRUE;

CREATE OR REPLACE VIEW v_recipe_reagent AS
SELECT
    selected_reagents.internal_id,
    selected_reagents.recipe_id,
    selected_reagents.item_id,
    selected_reagents.quantity,
    selected_reagents.name_id,
    selected_reagents.sort_order,
    selected_reagents.is_override
FROM recipe recipe_ids
    JOIN recipe_reagent selected_reagents
        ON selected_reagents.recipe_id = recipe_ids.id
        AND selected_reagents.is_override = TRUE
WHERE recipe_ids.is_override = TRUE
UNION ALL
SELECT
    base_reagents.internal_id,
    base_reagents.recipe_id,
    base_reagents.item_id,
    base_reagents.quantity,
    base_reagents.name_id,
    base_reagents.sort_order,
    base_reagents.is_override
FROM recipe base_recipe
    JOIN recipe_reagent base_reagents
        ON base_reagents.recipe_id = base_recipe.id
        AND base_reagents.is_override = FALSE
WHERE base_recipe.is_override = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM recipe_reagent override_reagents
      WHERE override_reagents.recipe_id = base_recipe.id
        AND override_reagents.is_override = TRUE
  );

CREATE OR REPLACE VIEW v_recipe_crafted_output AS
SELECT
    override_outputs.id,
    override_outputs.recipe_id,
    override_outputs.sort_order,
    override_outputs.is_override,
    override_outputs.crafted_item_id,
    override_outputs.crafted_quantity,
    override_outputs.required_skill_level
FROM recipe_crafted_output override_outputs
WHERE override_outputs.is_override = TRUE
UNION ALL
SELECT
    NULL AS id,
    effective_recipe.id AS recipe_id,
    0 AS sort_order,
    FALSE AS is_override,
    effective_recipe.crafted_item_id,
    COALESCE(NULLIF(effective_recipe.crafted_quantity, 0), 1) AS crafted_quantity,
    effective_recipe.required_skill_level
FROM v_recipe effective_recipe
WHERE effective_recipe.crafted_item_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM recipe_crafted_output override_outputs
      WHERE override_outputs.recipe_id = effective_recipe.id
        AND override_outputs.is_override = TRUE
  );
