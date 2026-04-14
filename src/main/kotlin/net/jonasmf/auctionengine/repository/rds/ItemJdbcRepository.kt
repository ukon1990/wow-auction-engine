package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.domain.item.InventoryType
import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.domain.item.ItemAppearanceReference
import net.jonasmf.auctionengine.domain.item.ItemBinding
import net.jonasmf.auctionengine.domain.item.ItemClass
import net.jonasmf.auctionengine.domain.item.ItemQuality
import net.jonasmf.auctionengine.domain.item.ItemSubclass
import net.jonasmf.auctionengine.dto.LocaleDTO
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

private const val ITEM_JDBC_CHUNK_SIZE = 200

data class ItemPersistenceSummary(
    val localesUpserted: Int,
    val itemQualitiesUpserted: Int,
    val inventoryTypesUpserted: Int,
    val itemBindingsUpserted: Int,
    val itemClassesUpserted: Int,
    val itemSubclassesUpserted: Int,
    val itemAppearanceReferencesUpserted: Int,
    val itemsUpserted: Int,
    val itemAppearanceLinksUpserted: Int,
)

@Repository
class ItemJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findDistinctAuctionItemIdsForDate(date: LocalDate): List<Int> =
        jdbcTemplate.query(
            """
            SELECT DISTINCT item_id
            FROM hourly_auction_stats
            WHERE date = ?
              AND pet_species_id = 0
              AND item_id > 0
            ORDER BY item_id
            """.trimIndent(),
            { rs, _ -> rs.getInt("item_id") },
            date,
        )

    fun findExistingItemIds(itemIds: Collection<Int>): Set<Int> {
        if (itemIds.isEmpty()) return emptySet()
        return itemIds
            .distinct()
            .chunked(ITEM_JDBC_CHUNK_SIZE)
            .flatMap { chunk ->
                jdbcTemplate.query(
                    "SELECT id FROM `item` WHERE id IN (${placeholders(chunk.size)})",
                    { rs, _ -> rs.getInt("id") },
                    *chunk.toTypedArray(),
                )
            }.toSet()
    }

    @Transactional
    fun syncItems(items: List<Item>): ItemPersistenceSummary {
        val groupedItems = items.distinctBy(Item::id)
        if (groupedItems.isEmpty()) {
            return ItemPersistenceSummary(0, 0, 0, 0, 0, 0, 0, 0, 0)
        }

        val localeRecords = buildLocaleRecords(groupedItems)
        upsertLocales(localeRecords.values.toList())
        val localeIds = findLocaleIds(localeRecords.keys)

        val qualities = groupedItems.map(Item::quality).distinctBy(ItemQuality::type)
        upsertItemQualities(qualities, localeIds)
        val qualityIds = findNaturalIds("item_quality", "type", qualities.map(ItemQuality::type))

        val inventoryTypes = groupedItems.map(Item::inventoryType).distinctBy(InventoryType::type)
        upsertInventoryTypes(inventoryTypes, localeIds)
        val inventoryTypeIds = findNaturalIds("inventory_type", "type", inventoryTypes.map(InventoryType::type))

        val bindings = groupedItems.mapNotNull(Item::binding).distinctBy(ItemBinding::type)
        upsertItemBindings(bindings, localeIds)
        val bindingIds = findNaturalIds("item_binding", "type", bindings.map(ItemBinding::type))

        val itemClasses = groupedItems.map(Item::itemClass).distinctBy(ItemClass::id)
        upsertItemClasses(itemClasses, localeIds)

        val itemSubclasses =
            groupedItems
                .map(Item::itemSubclass)
                .distinctBy { subclassKey(it.classId, it.subclassId) }
        upsertItemSubclasses(itemSubclasses, localeIds)
        val itemSubclassIds = findItemSubclassIds(itemSubclasses)

        val appearanceReferences =
            groupedItems
                .flatMap(Item::appearances)
                .distinctBy(ItemAppearanceReference::id)
        upsertItemAppearanceReferences(appearanceReferences)

        upsertItems(
            items = groupedItems,
            localeIds = localeIds,
            qualityIds = qualityIds,
            inventoryTypeIds = inventoryTypeIds,
            bindingIds = bindingIds,
            itemSubclassIds = itemSubclassIds,
        )
        upsertItemAppearanceLinks(groupedItems)

        return ItemPersistenceSummary(
            localesUpserted = localeRecords.size,
            itemQualitiesUpserted = qualities.size,
            inventoryTypesUpserted = inventoryTypes.size,
            itemBindingsUpserted = bindings.size,
            itemClassesUpserted = itemClasses.size,
            itemSubclassesUpserted = itemSubclasses.size,
            itemAppearanceReferencesUpserted = appearanceReferences.size,
            itemsUpserted = groupedItems.size,
            itemAppearanceLinksUpserted = groupedItems.sumOf { item -> item.appearances.distinctBy { it.id }.size },
        )
    }

    private fun buildLocaleRecords(items: List<Item>): Map<LocaleNaturalKey, LocaleRecord> {
        val records = linkedMapOf<LocaleNaturalKey, LocaleRecord>()
        items.forEach { item ->
            records[LocaleNaturalKey(LocaleSourceType.ITEM, localeSourceKey(item.id), "name")] =
                LocaleRecord(LocaleSourceType.ITEM, localeSourceKey(item.id), "name", item.name)

            records[LocaleNaturalKey(LocaleSourceType.ITEM_QUALITY, localeSourceKey(item.quality.type), "name")] =
                LocaleRecord(
                    LocaleSourceType.ITEM_QUALITY,
                    localeSourceKey(item.quality.type),
                    "name",
                    item.quality.name,
                )

            records[
                LocaleNaturalKey(
                    LocaleSourceType.INVENTORY_TYPE,
                    localeSourceKey(item.inventoryType.type),
                    "name",
                ),
            ] =
                LocaleRecord(
                    LocaleSourceType.INVENTORY_TYPE,
                    localeSourceKey(item.inventoryType.type),
                    "name",
                    item.inventoryType.name,
                )

            item.binding?.let { binding ->
                records[LocaleNaturalKey(LocaleSourceType.ITEM_BINDING, localeSourceKey(binding.type), "name")] =
                    LocaleRecord(
                        LocaleSourceType.ITEM_BINDING,
                        localeSourceKey(binding.type),
                        "name",
                        binding.name,
                    )
            }

            records[LocaleNaturalKey(LocaleSourceType.ITEM_CLASS, localeSourceKey(item.itemClass.id), "name")] =
                LocaleRecord(
                    LocaleSourceType.ITEM_CLASS,
                    localeSourceKey(item.itemClass.id),
                    "name",
                    item.itemClass.name,
                )

            records[
                LocaleNaturalKey(
                    LocaleSourceType.ITEM_SUBCLASS,
                    localeSourceKey(item.itemSubclass.classId, item.itemSubclass.subclassId),
                    "display_name",
                ),
            ] =
                LocaleRecord(
                    LocaleSourceType.ITEM_SUBCLASS,
                    localeSourceKey(item.itemSubclass.classId, item.itemSubclass.subclassId),
                    "display_name",
                    item.itemSubclass.displayName,
                )
        }
        return records
    }

    private fun upsertLocales(records: List<LocaleRecord>) {
        if (records.isEmpty()) return
        records.chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                buildString {
                    append(
                        """
                        INSERT INTO locale (
                            source_type,
                            source_key,
                            source_field,
                            en_us,
                            es_mx,
                            pt_br,
                            pt_pt,
                            de_de,
                            en_gb,
                            es_es,
                            fr_fr,
                            it_it,
                            ru_ru,
                            ko_kr,
                            zh_tw,
                            zh_cn
                        ) VALUES
                        """.trimIndent(),
                    )
                    append(' ')
                    append(chunk.joinToString(",") { "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" })
                    append(
                        """
                        
                        ON DUPLICATE KEY UPDATE
                            en_us = VALUES(en_us),
                            es_mx = VALUES(es_mx),
                            pt_br = VALUES(pt_br),
                            pt_pt = VALUES(pt_pt),
                            de_de = VALUES(de_de),
                            en_gb = VALUES(en_gb),
                            es_es = VALUES(es_es),
                            fr_fr = VALUES(fr_fr),
                            it_it = VALUES(it_it),
                            ru_ru = VALUES(ru_ru),
                            ko_kr = VALUES(ko_kr),
                            zh_tw = VALUES(zh_tw),
                            zh_cn = VALUES(zh_cn)
                        """.trimIndent(),
                    )
                }
            val params =
                chunk.flatMap { record ->
                    listOf(
                        record.sourceType,
                        record.sourceKey,
                        record.sourceField,
                        record.locale.en_US,
                        record.locale.es_MX,
                        record.locale.pt_BR,
                        record.locale.pt_PT,
                        record.locale.de_DE,
                        record.locale.en_GB,
                        record.locale.es_ES,
                        record.locale.fr_FR,
                        record.locale.it_IT,
                        record.locale.ru_RU,
                        record.locale.ko_KR,
                        record.locale.zh_TW,
                        record.locale.zh_CN,
                    )
                }
            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun findLocaleIds(keys: Collection<LocaleNaturalKey>): Map<LocaleNaturalKey, Long> {
        if (keys.isEmpty()) return emptyMap()
        val results = linkedMapOf<LocaleNaturalKey, Long>()
        keys.chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            val conditions = chunk.joinToString(" OR ") { "(source_type = ? AND source_key = ? AND source_field = ?)" }
            val params =
                chunk.flatMap { key -> listOf(key.sourceType, key.sourceKey, key.sourceField) }
            jdbcTemplate.query(
                "SELECT id, source_type, source_key, source_field FROM locale WHERE $conditions",
                { rs ->
                    val key =
                        LocaleNaturalKey(
                            rs.getString("source_type"),
                            rs.getString("source_key"),
                            rs.getString("source_field"),
                        )
                    results[key] = rs.getLong("id")
                },
                *params.toTypedArray(),
            )
        }
        return results
    }

    private fun upsertItemQualities(
        qualities: List<ItemQuality>,
        localeIds: Map<LocaleNaturalKey, Long>,
    ) {
        if (qualities.isEmpty()) return
        qualities.chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO item_quality (type, name_id)
                VALUES ${chunk.joinToString(",") { "(?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    name_id = VALUES(name_id)
                """.trimIndent()
            val params =
                chunk.flatMap { quality ->
                    listOf<Any?>(
                        quality.type,
                        localeIds.getValue(
                            LocaleNaturalKey(LocaleSourceType.ITEM_QUALITY, localeSourceKey(quality.type), "name"),
                        ),
                    )
                }
            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun upsertInventoryTypes(
        inventoryTypes: List<InventoryType>,
        localeIds: Map<LocaleNaturalKey, Long>,
    ) {
        if (inventoryTypes.isEmpty()) return
        inventoryTypes.chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO inventory_type (type, name_id)
                VALUES ${chunk.joinToString(",") { "(?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    name_id = VALUES(name_id)
                """.trimIndent()
            val params =
                chunk.flatMap { inventoryType ->
                    listOf<Any?>(
                        inventoryType.type,
                        localeIds.getValue(
                            LocaleNaturalKey(
                                LocaleSourceType.INVENTORY_TYPE,
                                localeSourceKey(inventoryType.type),
                                "name",
                            ),
                        ),
                    )
                }
            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun upsertItemBindings(
        bindings: List<ItemBinding>,
        localeIds: Map<LocaleNaturalKey, Long>,
    ) {
        if (bindings.isEmpty()) return
        bindings.chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO item_binding (type, name_id)
                VALUES ${chunk.joinToString(",") { "(?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    name_id = VALUES(name_id)
                """.trimIndent()
            val params =
                chunk.flatMap { binding ->
                    listOf<Any?>(
                        binding.type,
                        localeIds.getValue(
                            LocaleNaturalKey(LocaleSourceType.ITEM_BINDING, localeSourceKey(binding.type), "name"),
                        ),
                    )
                }
            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun upsertItemClasses(
        itemClasses: List<ItemClass>,
        localeIds: Map<LocaleNaturalKey, Long>,
    ) {
        if (itemClasses.isEmpty()) return
        itemClasses.chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO item_class (id, name_id)
                VALUES ${chunk.joinToString(",") { "(?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    name_id = VALUES(name_id)
                """.trimIndent()
            val params =
                chunk.flatMap { itemClass ->
                    listOf<Any?>(
                        itemClass.id,
                        localeIds.getValue(
                            LocaleNaturalKey(LocaleSourceType.ITEM_CLASS, localeSourceKey(itemClass.id), "name"),
                        ),
                    )
                }
            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun upsertItemSubclasses(
        itemSubclasses: List<ItemSubclass>,
        localeIds: Map<LocaleNaturalKey, Long>,
    ) {
        if (itemSubclasses.isEmpty()) return
        itemSubclasses.chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO item_subclass (class_id, subclass_id, display_name_id, hide_subclass_in_tooltips)
                VALUES ${chunk.joinToString(",") { "(?, ?, ?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    display_name_id = VALUES(display_name_id),
                    hide_subclass_in_tooltips = VALUES(hide_subclass_in_tooltips)
                """.trimIndent()
            val params =
                chunk.flatMap { itemSubclass ->
                    listOf<Any?>(
                        itemSubclass.classId,
                        itemSubclass.subclassId,
                        localeIds.getValue(
                            LocaleNaturalKey(
                                LocaleSourceType.ITEM_SUBCLASS,
                                localeSourceKey(itemSubclass.classId, itemSubclass.subclassId),
                                "display_name",
                            ),
                        ),
                        itemSubclass.hideSubclassInTooltips,
                    )
                }
            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun findItemSubclassIds(itemSubclasses: Collection<ItemSubclass>): Map<String, Long> {
        if (itemSubclasses.isEmpty()) return emptyMap()
        val results = linkedMapOf<String, Long>()
        itemSubclasses
            .distinctBy { subclassKey(it.classId, it.subclassId) }
            .chunked(ITEM_JDBC_CHUNK_SIZE)
            .forEach { chunk ->
                val conditions = chunk.joinToString(" OR ") { "(class_id = ? AND subclass_id = ?)" }
                val params = chunk.flatMap { listOf(it.classId, it.subclassId) }
                jdbcTemplate.query(
                    "SELECT internal_id, class_id, subclass_id FROM item_subclass WHERE $conditions",
                    { rs ->
                        results[subclassKey(rs.getInt("class_id"), rs.getInt("subclass_id"))] =
                            rs.getLong("internal_id")
                    },
                    *params.toTypedArray(),
                )
            }
        return results
    }

    private fun upsertItemAppearanceReferences(appearanceReferences: List<ItemAppearanceReference>) {
        if (appearanceReferences.isEmpty()) return
        appearanceReferences.chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO item_appearance_ref (id, href)
                VALUES ${chunk.joinToString(",") { "(?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    href = VALUES(href)
                """.trimIndent()
            val params =
                chunk.flatMap { appearanceReference ->
                    listOf<Any?>(appearanceReference.id, appearanceReference.href)
                }
            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun upsertItems(
        items: List<Item>,
        localeIds: Map<LocaleNaturalKey, Long>,
        qualityIds: Map<String, Long>,
        inventoryTypeIds: Map<String, Long>,
        bindingIds: Map<String, Long>,
        itemSubclassIds: Map<String, Long>,
    ) {
        items.chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO `item` (
                    id,
                    name_id,
                    quality_id,
                    level,
                    required_level,
                    media_url,
                    item_class_id,
                    item_subclass_id,
                    inventory_type_id,
                    binding_id,
                    purchase_price,
                    sell_price,
                    max_count,
                    is_equippable,
                    is_stackable,
                    purchase_quantity
                ) VALUES ${chunk.joinToString(",") { "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    name_id = VALUES(name_id),
                    quality_id = VALUES(quality_id),
                    level = VALUES(level),
                    required_level = VALUES(required_level),
                    media_url = VALUES(media_url),
                    item_class_id = VALUES(item_class_id),
                    item_subclass_id = VALUES(item_subclass_id),
                    inventory_type_id = VALUES(inventory_type_id),
                    binding_id = VALUES(binding_id),
                    purchase_price = VALUES(purchase_price),
                    sell_price = VALUES(sell_price),
                    max_count = VALUES(max_count),
                    is_equippable = VALUES(is_equippable),
                    is_stackable = VALUES(is_stackable),
                    purchase_quantity = VALUES(purchase_quantity)
                """.trimIndent()
            val params =
                chunk.flatMap { item ->
                    listOf<Any?>(
                        item.id,
                        localeIds.getValue(LocaleNaturalKey(LocaleSourceType.ITEM, localeSourceKey(item.id), "name")),
                        qualityIds.getValue(item.quality.type),
                        item.level,
                        item.requiredLevel,
                        item.mediaUrl,
                        item.itemClass.id,
                        itemSubclassIds.getValue(subclassKey(item.itemSubclass.classId, item.itemSubclass.subclassId)),
                        inventoryTypeIds.getValue(item.inventoryType.type),
                        item.binding?.type?.let(bindingIds::get),
                        item.purchasePrice,
                        item.sellPrice,
                        item.maxCount,
                        item.isEquippable,
                        item.isStackable,
                        item.purchaseQuantity,
                    )
                }
            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun upsertItemAppearanceLinks(items: List<Item>) {
        val links =
            items.flatMap { item ->
                item.appearances.distinctBy(ItemAppearanceReference::id).map { appearance -> item.id to appearance.id }
            }
        if (links.isEmpty()) return
        links.chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            val sql =
                """
                INSERT INTO item_appearance_refs (item_id, appearance_ref_id)
                VALUES ${chunk.joinToString(",") { "(?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    appearance_ref_id = VALUES(appearance_ref_id)
                """.trimIndent()
            val params = chunk.flatMap { (itemId, appearanceRefId) -> listOf(itemId, appearanceRefId) }
            jdbcTemplate.update(sql, *params.toTypedArray())
        }
    }

    private fun findNaturalIds(
        tableName: String,
        keyColumn: String,
        keys: Collection<String>,
    ): Map<String, Long> {
        if (keys.isEmpty()) return emptyMap()
        val results = linkedMapOf<String, Long>()
        keys.distinct().chunked(ITEM_JDBC_CHUNK_SIZE).forEach { chunk ->
            jdbcTemplate.query(
                "SELECT internal_id, $keyColumn FROM $tableName WHERE $keyColumn IN (${placeholders(chunk.size)})",
                { rs -> results[rs.getString(keyColumn)] = rs.getLong("internal_id") },
                *chunk.toTypedArray(),
            )
        }
        return results
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")

    private fun subclassKey(
        classId: Int,
        subclassId: Int,
    ): String = "$classId:$subclassId"
}

private data class LocaleNaturalKey(
    val sourceType: String,
    val sourceKey: String,
    val sourceField: String,
)

private data class LocaleRecord(
    val sourceType: String,
    val sourceKey: String,
    val sourceField: String,
    val locale: LocaleDTO,
)
