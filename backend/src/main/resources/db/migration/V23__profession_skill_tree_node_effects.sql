CREATE TABLE profession_skill_tree_node_effect (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id BIGINT NOT NULL,
    effect_type VARCHAR(32) NOT NULL,
    skill_bonus INT NOT NULL,
    crafting_category VARCHAR(128) NULL,
    unlock_rank INT NOT NULL DEFAULT 0,
    source VARCHAR(32) NOT NULL DEFAULT 'description',
    CONSTRAINT fk_skill_tree_node_effect_node FOREIGN KEY (node_id) REFERENCES profession_skill_tree_node (id) ON DELETE CASCADE,
    UNIQUE KEY uk_node_skill_effect (node_id, effect_type, skill_bonus, crafting_category)
);
