CREATE TABLE recipe_crafting_rule (
    recipe_id INT NOT NULL PRIMARY KEY,
    base_skill DECIMAL(10, 2) NULL,
    base_difficulty DECIMAL(10, 2) NULL,
    bonus_skill DECIMAL(10, 2) NULL,
    quality_thresholds JSON NULL,
    required_reagent_skill_delta DECIMAL(10, 2) NULL,
    max_quality_required_reagents JSON NULL,
    source_import_id BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_recipe_crafting_rule_import FOREIGN KEY (source_import_id) REFERENCES normalized_profession_import (id)
);
