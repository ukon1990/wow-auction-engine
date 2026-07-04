ALTER TABLE recipe_reagent
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0,
    ADD COLUMN is_override BOOLEAN NOT NULL DEFAULT FALSE;

SET @recipe_reagent_rank := 0;
SET @recipe_reagent_recipe_id := NULL;

UPDATE recipe_reagent
JOIN (
    SELECT
        internal_id,
        recipe_id,
        CASE
            WHEN @recipe_reagent_recipe_id = recipe_id THEN @recipe_reagent_rank := @recipe_reagent_rank + 1
            ELSE @recipe_reagent_rank := 0
        END AS computed_sort_order,
        @recipe_reagent_recipe_id := recipe_id
    FROM recipe_reagent
    ORDER BY recipe_id, internal_id
) ordered_reagents ON ordered_reagents.internal_id = recipe_reagent.internal_id
SET recipe_reagent.sort_order = ordered_reagents.computed_sort_order;

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
