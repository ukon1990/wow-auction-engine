-- Refresh after V18 adds item.rank so cloned databases rebuild the view projection.
CREATE OR REPLACE VIEW v_item AS
SELECT
    item_ids.id,
    override_item.id IS NOT NULL AS is_override,
    COALESCE(override_item.is_equippable, base_item.is_equippable) AS is_equippable,
    COALESCE(override_item.is_stackable, base_item.is_stackable) AS is_stackable,
    COALESCE(override_item.level, base_item.level) AS level,
    COALESCE(override_item.rank, base_item.rank) AS rank,
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
