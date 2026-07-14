ALTER TABLE profession_skill_tree_node
    MODIFY COLUMN name VARCHAR(255) NULL;

ALTER TABLE profession_skill_tree_entry
    MODIFY COLUMN name VARCHAR(255) NULL;

UPDATE profession_skill_tree_node
SET name = NULL
WHERE name = CONCAT('Node ', external_node_id);

UPDATE profession_skill_tree_entry
SET name = NULL
WHERE name = CONCAT('Entry ', external_entry_id);
