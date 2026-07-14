ALTER TABLE user_character_profession_profile
    ADD COLUMN blizzard_synced_at TIMESTAMP NULL AFTER source_import_id;

ALTER TABLE user_character_profession_recipe
    MODIFY source_import_id BIGINT NULL,
    ADD COLUMN blizzard_synced_at TIMESTAMP NULL AFTER source_import_id;
