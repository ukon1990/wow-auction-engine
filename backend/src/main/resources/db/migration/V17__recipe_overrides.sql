ALTER TABLE recipe_reagent
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0,
    ADD COLUMN is_override BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE recipe_reagent rr
JOIN (
    SELECT
        internal_id,
        ROW_NUMBER() OVER (PARTITION BY recipe_id ORDER BY internal_id) - 1 AS computed_sort_order
    FROM recipe_reagent
) ordered_reagents ON ordered_reagents.internal_id = rr.internal_id
SET rr.sort_order = ordered_reagents.computed_sort_order;

ALTER TABLE recipe
    ADD COLUMN is_override BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN required_skill_level INT DEFAULT NULL,
    ADD COLUMN override_note VARCHAR(512) DEFAULT NULL,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE recipe
    DROP INDEX UKj5a48e5qk1agutsdwdeu3ifoq,
    DROP INDEX UKfsqhfjnj40quo0h1b8xhvkxpw,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, is_override),
    ADD UNIQUE KEY uk_recipe_description_id_override (description_id, is_override),
    ADD UNIQUE KEY uk_recipe_name_id_override (name_id, is_override),
    ADD KEY idx_recipe_override_list (is_override, updated_at),
    ADD KEY idx_recipe_crafted_item_override (crafted_item_id, is_override),
    ADD CONSTRAINT chk_recipe_base_required_fields
        CHECK (
            is_override = TRUE
            OR (
                name_id IS NOT NULL
                AND media_url IS NOT NULL
                AND profession_category_id IS NOT NULL
            )
        );

ALTER TABLE recipe_reagent
    DROP FOREIGN KEY FKjtyx64w8r7k755pweumybcctk,
    DROP INDEX UKijuu0en5r85ybhflyeti97lwg,
    ADD UNIQUE KEY uk_recipe_reagent_name_override (name_id, is_override),
    ADD UNIQUE KEY uk_recipe_reagent_recipe_sort_override (recipe_id, sort_order, is_override),
    ADD KEY idx_recipe_reagent_item_override (item_id, is_override),
    ADD CONSTRAINT fk_recipe_reagent_recipe_base
        FOREIGN KEY (recipe_id, is_override) REFERENCES recipe (id, is_override)
        ON DELETE CASCADE;

CREATE TABLE recipe_crafted_output (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipe_id INT NOT NULL,
    sort_order INT NOT NULL,
    is_override BOOLEAN NOT NULL DEFAULT TRUE,
    crafted_item_id INT NOT NULL,
    crafted_quantity INT NOT NULL,
    required_skill_level INT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_crafted_output_sort_override (recipe_id, sort_order, is_override),
    KEY idx_recipe_crafted_output_item_override (crafted_item_id, is_override),
    KEY idx_recipe_crafted_output_recipe_override (recipe_id, is_override),
    CONSTRAINT fk_recipe_crafted_output_recipe
        FOREIGN KEY (recipe_id, is_override) REFERENCES recipe (id, is_override)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

CREATE TABLE recipe_reagent_rank (
    recipe_reagent_id BIGINT NOT NULL,
    rank TINYINT NOT NULL,
    is_override BOOLEAN NOT NULL DEFAULT TRUE,
    item_id INT NOT NULL,
    skill_points INT DEFAULT NULL,
    PRIMARY KEY (recipe_reagent_id, rank, is_override),
    KEY idx_recipe_reagent_rank_item (item_id),
    CONSTRAINT fk_recipe_reagent_rank_reagent
        FOREIGN KEY (recipe_reagent_id) REFERENCES recipe_reagent (internal_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_recipe_reagent_rank
        CHECK (rank BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

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
    SELECT DISTINCT id
    FROM recipe
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
