ALTER TABLE item
    ADD COLUMN rank INT NULL,
    ADD KEY idx_item_rank (rank);

UPDATE item ranked_item
JOIN (
    SELECT
        i.id,
        i.is_override,
        ROW_NUMBER() OVER (
            PARTITION BY LOWER(TRIM(COALESCE(l.en_us, l.en_gb, l.de_de, l.es_es, l.fr_fr, l.it_it, l.ko_kr, l.pt_br, l.ru_ru, l.zh_cn)))
            ORDER BY i.id
        ) AS item_rank
    FROM item i
    JOIN locale l ON l.id = i.name_id
    WHERE i.is_override = FALSE
) ranks
    ON ranks.id = ranked_item.id
    AND ranks.is_override = ranked_item.is_override
SET ranked_item.rank = ranks.item_rank
WHERE ranked_item.is_override = FALSE;
