CREATE TABLE profession_tree_import (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_type VARCHAR(64) NOT NULL,
    source_version VARCHAR(64) DEFAULT NULL,
    addon_version VARCHAR(64) DEFAULT NULL,
    schema_version INT DEFAULT NULL,
    game_build VARCHAR(64) DEFAULT NULL,
    content_hash CHAR(64) NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    diagnostic VARCHAR(512) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profession_tree_import_source_hash (source_type, content_hash),
    KEY idx_profession_tree_import_imported_at (imported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE profession_skill_tree (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expansion_id INT NOT NULL,
    profession_id INT NOT NULL,
    skill_line_id INT DEFAULT NULL,
    config_id BIGINT DEFAULT NULL,
    external_tree_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT DEFAULT NULL,
    import_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profession_skill_tree_profession_config (profession_id, config_id),
    KEY idx_profession_skill_tree_profession_expansion (profession_id, expansion_id),
    KEY idx_profession_skill_tree_expansion (expansion_id),
    KEY idx_profession_skill_tree_skill_line (skill_line_id),
    CONSTRAINT fk_profession_skill_tree_expansion FOREIGN KEY (expansion_id) REFERENCES expansion (id),
    CONSTRAINT fk_profession_skill_tree_profession FOREIGN KEY (profession_id) REFERENCES profession (id),
    CONSTRAINT fk_profession_skill_tree_import FOREIGN KEY (import_id) REFERENCES profession_tree_import (id),
    CONSTRAINT fk_profession_skill_tree_skill_line FOREIGN KEY (skill_line_id) REFERENCES skill_tier (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE profession_skill_tree_tab (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tree_id BIGINT NOT NULL,
    external_tab_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT DEFAULT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profession_skill_tree_tab_tree_external (tree_id, external_tab_id),
    KEY idx_profession_skill_tree_tab_tree_order (tree_id, display_order),
    CONSTRAINT fk_profession_skill_tree_tab_tree FOREIGN KEY (tree_id) REFERENCES profession_skill_tree (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE profession_skill_tree_node (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tree_id BIGINT NOT NULL,
    tab_id BIGINT DEFAULT NULL,
    external_node_id INT NOT NULL,
    name VARCHAR(255) DEFAULT NULL,
    description TEXT DEFAULT NULL,
    max_ranks INT NOT NULL,
    required_rank INT NOT NULL DEFAULT 0,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profession_skill_tree_node_tree_external (tree_id, external_node_id),
    KEY idx_profession_skill_tree_node_tab_order (tab_id, display_order),
    CONSTRAINT fk_profession_skill_tree_node_tree FOREIGN KEY (tree_id) REFERENCES profession_skill_tree (id) ON DELETE CASCADE,
    CONSTRAINT fk_profession_skill_tree_node_tab FOREIGN KEY (tab_id) REFERENCES profession_skill_tree_tab (id) ON DELETE SET NULL,
    CONSTRAINT chk_profession_skill_tree_node_ranks CHECK (max_ranks > 0 AND required_rank >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE profession_skill_tree_node_parent (
    node_id BIGINT NOT NULL,
    parent_node_id BIGINT NOT NULL,
    required_parent_ranks INT NOT NULL DEFAULT 1,
    PRIMARY KEY (node_id, parent_node_id),
    KEY idx_profession_skill_tree_node_parent_parent (parent_node_id),
    CONSTRAINT fk_profession_skill_tree_node_parent_node FOREIGN KEY (node_id) REFERENCES profession_skill_tree_node (id) ON DELETE CASCADE,
    CONSTRAINT fk_profession_skill_tree_node_parent_parent FOREIGN KEY (parent_node_id) REFERENCES profession_skill_tree_node (id) ON DELETE CASCADE,
    CONSTRAINT chk_profession_skill_tree_node_parent_ranks CHECK (required_parent_ranks > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE profession_skill_tree_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    node_id BIGINT NOT NULL,
    external_entry_id INT NOT NULL,
    name VARCHAR(255) DEFAULT NULL,
    description TEXT DEFAULT NULL,
    rank_limit INT NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profession_skill_tree_entry_node_external (node_id, external_entry_id),
    KEY idx_profession_skill_tree_entry_node_order (node_id, display_order),
    CONSTRAINT fk_profession_skill_tree_entry_node FOREIGN KEY (node_id) REFERENCES profession_skill_tree_node (id) ON DELETE CASCADE,
    CONSTRAINT chk_profession_skill_tree_entry_rank_limit CHECK (rank_limit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_character (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_subject VARCHAR(255) NOT NULL,
    region VARCHAR(32) NOT NULL,
    realm_name VARCHAR(255) NOT NULL,
    character_name VARCHAR(255) NOT NULL,
    source_guid VARCHAR(64) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_character_owner_location_name (owner_subject, region, realm_name, character_name),
    UNIQUE KEY uk_user_character_owner_source_guid (owner_subject, source_guid),
    KEY idx_user_character_owner_updated (owner_subject, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_character_profession_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    profession_id INT NOT NULL,
    skill_level INT DEFAULT NULL,
    tree_id BIGINT DEFAULT NULL,
    source_import_id BIGINT DEFAULT NULL,
    blizzard_synced_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_character_profession_profile_character_profession (character_id, profession_id),
    KEY idx_user_character_profession_profile_tree (tree_id),
    CONSTRAINT fk_user_character_profession_profile_character FOREIGN KEY (character_id) REFERENCES user_character (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_character_profession_profile_profession FOREIGN KEY (profession_id) REFERENCES profession (id),
    CONSTRAINT fk_user_character_profession_profile_tree FOREIGN KEY (tree_id) REFERENCES profession_skill_tree (id),
    CONSTRAINT fk_user_character_profession_profile_import FOREIGN KEY (source_import_id) REFERENCES profession_tree_import (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_character_profession_allocation (
    profile_id BIGINT NOT NULL,
    entry_id BIGINT NOT NULL,
    rank INT NOT NULL,
    PRIMARY KEY (profile_id, entry_id),
    KEY idx_user_character_profession_allocation_entry (entry_id),
    CONSTRAINT fk_user_character_profession_allocation_profile FOREIGN KEY (profile_id) REFERENCES user_character_profession_profile (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_character_profession_allocation_entry FOREIGN KEY (entry_id) REFERENCES profession_skill_tree_entry (id),
    CONSTRAINT chk_user_character_profession_allocation_rank CHECK (rank > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
