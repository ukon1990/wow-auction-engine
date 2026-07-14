CREATE INDEX idx_user_character_profession_profile_profession_tree
    ON user_character_profession_profile (profession_id, tree_id);

CREATE INDEX idx_user_character_owner_subject
    ON user_character (owner_subject);
