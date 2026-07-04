ALTER TABLE `item`
    ADD COLUMN is_override BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN override_note VARCHAR(512) DEFAULT NULL,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE `item`
    MODIFY COLUMN is_equippable BIT(1) DEFAULT NULL,
    MODIFY COLUMN is_stackable BIT(1) DEFAULT NULL,
    MODIFY COLUMN level INT(11) DEFAULT NULL,
    MODIFY COLUMN max_count INT(11) DEFAULT NULL,
    MODIFY COLUMN purchase_price INT(11) DEFAULT NULL,
    MODIFY COLUMN purchase_quantity INT(11) DEFAULT NULL,
    MODIFY COLUMN required_level INT(11) DEFAULT NULL,
    MODIFY COLUMN sell_price INT(11) DEFAULT NULL;

ALTER TABLE `item`
    ADD UNIQUE KEY uk_item_name_id_override (name_id, is_override);

ALTER TABLE `item`
    DROP INDEX UKcgrg884xedtb5d2ysbg4qis7d,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, is_override),
    ADD KEY idx_item_override_updated_at (is_override, updated_at),
    ADD KEY idx_item_override_expansion_id (is_override, expansion_id),
    ADD CONSTRAINT chk_item_base_required_fields
        CHECK (
            is_override = TRUE
            OR (
                is_equippable IS NOT NULL
                AND is_stackable IS NOT NULL
                AND level IS NOT NULL
                AND max_count IS NOT NULL
                AND purchase_price IS NOT NULL
                AND purchase_quantity IS NOT NULL
                AND required_level IS NOT NULL
                AND sell_price IS NOT NULL
            )
        );

CREATE OR REPLACE VIEW v_item AS
SELECT
    item_ids.id,
    override_item.id IS NOT NULL AS is_override,
    COALESCE(override_item.is_equippable, base_item.is_equippable) AS is_equippable,
    COALESCE(override_item.is_stackable, base_item.is_stackable) AS is_stackable,
    COALESCE(override_item.level, base_item.level) AS level,
    COALESCE(override_item.max_count, base_item.max_count) AS max_count,
    COALESCE(override_item.media_url, base_item.media_url) AS media_url,
    COALESCE(override_item.media_source_url, base_item.media_source_url) AS media_source_url,
    COALESCE(override_item.purchase_price, base_item.purchase_price) AS purchase_price,
    COALESCE(override_item.purchase_quantity, base_item.purchase_quantity) AS purchase_quantity,
    COALESCE(override_item.required_level, base_item.required_level) AS required_level,
    COALESCE(override_item.sell_price, base_item.sell_price) AS sell_price,
    COALESCE(override_item.binding_id, base_item.binding_id) AS binding_id,
    COALESCE(override_item.inventory_type_id, base_item.inventory_type_id) AS inventory_type_id,
    COALESCE(override_item.item_class_id, base_item.item_class_id) AS item_class_id,
    COALESCE(override_item.item_subclass_id, base_item.item_subclass_id) AS item_subclass_id,
    COALESCE(override_item.name_id, base_item.name_id) AS name_id,
    COALESCE(override_item.quality_id, base_item.quality_id) AS quality_id,
    COALESCE(override_item.expansion_id, base_item.expansion_id) AS expansion_id,
    override_item.override_note,
    COALESCE(override_item.created_at, base_item.created_at) AS created_at,
    COALESCE(override_item.updated_at, base_item.updated_at) AS updated_at
FROM (
    SELECT DISTINCT id
    FROM `item`
) item_ids
    LEFT JOIN `item` base_item
        ON base_item.id = item_ids.id
        AND base_item.is_override = FALSE
    LEFT JOIN `item` override_item
        ON override_item.id = item_ids.id
        AND override_item.is_override = TRUE;
