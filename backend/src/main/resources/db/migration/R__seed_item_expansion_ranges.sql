INSERT INTO expansion (id, slug, name, major_version, display_order)
VALUES
    (1, 'vanilla', 'Vanilla', 1, 10),
    (2, 'the-burning-crusade', 'The Burning Crusade', 2, 20),
    (3, 'wrath-of-the-lich-king', 'Wrath of the Lich King', 3, 30),
    (4, 'cataclysm', 'Cataclysm', 4, 40),
    (5, 'mists-of-pandaria', 'Mists of Pandaria', 5, 50),
    (6, 'warlords-of-draenor', 'Warlords of Draenor', 6, 60),
    (7, 'legion', 'Legion', 7, 70),
    (8, 'battle-for-azeroth', 'Battle for Azeroth', 8, 80),
    (9, 'shadowlands', 'Shadowlands', 9, 90),
    (10, 'dragonflight', 'Dragonflight', 10, 100),
    (11, 'the-war-within', 'The War Within', 11, 110),
    (12, 'midnight', 'Midnight', 12, 120)
ON DUPLICATE KEY UPDATE
    slug = VALUES(slug),
    name = VALUES(name),
    major_version = VALUES(major_version),
    display_order = VALUES(display_order);

-- Generated ItemVersion ranges are maintained by tools/generate-expansion-ranges.mjs.
-- Manual ranges are stored with source='manual' and are intentionally not touched by regeneration.
