ALTER TABLE `item`
    ADD COLUMN `is_override` BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN `override_note` VARCHAR(512) NULL,
    ADD COLUMN `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE `item`
    MODIFY COLUMN `is_equippable` bit(1) NULL,
    MODIFY COLUMN `is_stackable` bit(1) NULL,
    MODIFY COLUMN `level` int(11) NULL,
    MODIFY COLUMN `max_count` int(11) NULL,
    MODIFY COLUMN `purchase_price` int(11) NULL,
    MODIFY COLUMN `purchase_quantity` int(11) NULL,
    MODIFY COLUMN `required_level` int(11) NULL,
    MODIFY COLUMN `sell_price` int(11) NULL;

ALTER TABLE `item`
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`id`, `is_override`);

CREATE INDEX idx_item_override_list ON `item` (`is_override`, `updated_at`);
CREATE INDEX idx_item_expansion_override ON `item` (`is_override`, `expansion_id`);
