ALTER TABLE profession_skill_tree
    ADD COLUMN skill_line_id INT DEFAULT NULL AFTER profession_id,
    ADD COLUMN config_id BIGINT DEFAULT NULL AFTER skill_line_id,
    ADD KEY idx_profession_skill_tree_skill_line (skill_line_id),
    ADD CONSTRAINT fk_profession_skill_tree_skill_line
        FOREIGN KEY (skill_line_id) REFERENCES skill_tier (id);

UPDATE profession_skill_tree SET config_id = external_tree_id WHERE config_id IS NULL;

ALTER TABLE profession_skill_tree
    ADD KEY idx_profession_skill_tree_expansion (expansion_id),
    DROP INDEX uk_profession_skill_tree_expansion_profession_external,
    ADD UNIQUE KEY uk_profession_skill_tree_profession_config (profession_id, config_id);

ALTER TABLE profession_skill_tree_node
    DROP CONSTRAINT chk_profession_skill_tree_node_ranks,
    ADD CONSTRAINT chk_profession_skill_tree_node_ranks CHECK (max_ranks > 0 AND required_rank >= 0);
