CREATE TABLE user_character_profession_recipe (
    profile_id BIGINT NOT NULL,
    recipe_id INT NOT NULL,
    recipe_name VARCHAR(255) NOT NULL,
    learned BOOLEAN NOT NULL,
    source_import_id BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id, recipe_id),
    KEY idx_user_character_profession_recipe_import (source_import_id),
    CONSTRAINT fk_user_character_profession_recipe_profile
        FOREIGN KEY (profile_id) REFERENCES user_character_profession_profile (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_character_profession_recipe_import
        FOREIGN KEY (source_import_id) REFERENCES normalized_profession_import (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
