CREATE TABLE normalized_profession_import (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_hash CHAR(64) NOT NULL,
    contract_version INT NOT NULL,
    addon VARCHAR(64) NOT NULL,
    addon_version VARCHAR(64) DEFAULT NULL,
    processor_version VARCHAR(64) NOT NULL,
    source_files JSON NOT NULL,
    character_count INT NOT NULL,
    profession_count INT NOT NULL,
    recipe_count INT NOT NULL,
    payload JSON NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_normalized_profession_import_content_hash (content_hash),
    KEY idx_normalized_profession_import_imported_at (imported_at),
    CONSTRAINT chk_normalized_profession_import_counts CHECK (
        character_count >= 0 AND profession_count >= 0 AND recipe_count >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_character_profession_recipe (
    profile_id BIGINT NOT NULL,
    recipe_id INT NOT NULL,
    recipe_name VARCHAR(255) NOT NULL,
    learned BOOLEAN NOT NULL,
    source_import_id BIGINT DEFAULT NULL,
    blizzard_synced_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id, recipe_id),
    KEY idx_user_character_profession_recipe_import (source_import_id),
    CONSTRAINT fk_user_character_profession_recipe_profile
        FOREIGN KEY (profile_id) REFERENCES user_character_profession_profile (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_character_profession_recipe_import
        FOREIGN KEY (source_import_id) REFERENCES normalized_profession_import (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
