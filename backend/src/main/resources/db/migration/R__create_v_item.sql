CREATE OR REPLACE VIEW v_item AS
WITH ids AS (SELECT DISTINCT id FROM item)
SELECT
    ids.id,
    COALESCE(o.name_id, b.name_id) AS name_id,
    COALESCE(o.quality_id, b.quality_id) AS quality_id,
    COALESCE(o.level, b.level) AS level,
    COALESCE(o.max_count, b.max_count) AS max_count,
    COALESCE(o.media_url, b.media_url) AS media_url,
    COALESCE(o.media_source_url, b.media_source_url) AS media_source_url,
    COALESCE(o.purchase_price, b.purchase_price) AS purchase_price,
    COALESCE(o.purchase_quantity, b.purchase_quantity) AS purchase_quantity,
    COALESCE(o.required_level, b.required_level) AS required_level,
    COALESCE(o.sell_price, b.sell_price) AS sell_price,
    COALESCE(o.binding_id, b.binding_id) AS binding_id,
    COALESCE(o.inventory_type_id, b.inventory_type_id) AS inventory_type_id,
    COALESCE(o.item_class_id, b.item_class_id) AS item_class_id,
    COALESCE(o.item_subclass_id, b.item_subclass_id) AS item_subclass_id,
    COALESCE(o.expansion_id, b.expansion_id) AS expansion_id,
    COALESCE(o.is_equippable, b.is_equippable) AS is_equippable,
    COALESCE(o.is_stackable, b.is_stackable) AS is_stackable
FROM ids
    LEFT JOIN item b ON b.id = ids.id AND b.is_override = FALSE
    LEFT JOIN item o ON o.id = ids.id AND o.is_override = TRUE;
