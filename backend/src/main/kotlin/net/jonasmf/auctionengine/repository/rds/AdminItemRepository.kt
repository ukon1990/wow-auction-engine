package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.generated.model.AdminItem
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideSnapshot
import net.jonasmf.auctionengine.generated.model.AdminItemPage
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.mapper.toGameLocale
import net.jonasmf.auctionengine.mapper.toLocaleDTO
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import net.jonasmf.auctionengine.repository.rds.getNullableInt
import net.jonasmf.auctionengine.repository.rds.getNullableLong
import net.jonasmf.auctionengine.repository.rds.toLocaleDTO
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class AdminItemSearchQuery(
    val page: Int = 0,
    val pageSize: Int = 50,
    val itemId: Int? = null,
    val name: String? = null,
    val qualityId: Long? = null,
    val classId: Int? = null,
    val subclassId: Int? = null,
    val expansionId: Int? = null,
    val hasOverride: Boolean? = null,
    val sort: String = "id",
    val localeColumnSuffix: String = AdminExpansionRepository.DEFAULT_LOCALE_COLUMN_SUFFIX,
)

data class ItemRow(
    val id: Int,
    val isOverride: Boolean,
    val nameId: Long?,
    val qualityId: Long?,
    val level: Int?,
    val maxCount: Int?,
    val mediaUrl: String?,
    val mediaSourceUrl: String?,
    val purchasePrice: Int?,
    val purchaseQuantity: Int?,
    val requiredLevel: Int?,
    val sellPrice: Int?,
    val bindingId: Long?,
    val inventoryTypeId: Long?,
    val itemClassId: Int?,
    val itemSubclassId: Long?,
    val expansionId: Int?,
    val isEquippable: Boolean?,
    val isStackable: Boolean?,
    val overrideNote: String?,
    val createdAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime?,
)

@Repository
class AdminItemRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val localeJdbcRepository: LocaleJdbcRepository,
) {
    fun itemExists(itemId: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `item` WHERE id = ?",
            Long::class.java,
            itemId,
        )!! > 0

    fun hasBaseRow(itemId: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `item` WHERE id = ? AND is_override = FALSE",
            Long::class.java,
            itemId,
        )!! > 0

    fun hasOverrideRow(itemId: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `item` WHERE id = ? AND is_override = TRUE",
            Long::class.java,
            itemId,
        )!! > 0

    fun search(query: AdminItemSearchQuery): AdminItemPage {
        val normalizedPage = query.page.coerceAtLeast(0)
        val normalizedPageSize = query.pageSize.coerceIn(1, 100)
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        query.itemId?.let {
            conditions += "v.id = ?"
            params += it
        }
        query.name?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            conditions += "COALESCE(nl.${query.localeColumnSuffix}, nl.en_gb, nl.en_us) LIKE ?"
            params += "%$name%"
        }
        query.qualityId?.let {
            conditions += "v.quality_id = ?"
            params += it
        }
        query.classId?.let {
            conditions += "v.item_class_id = ?"
            params += it
        }
        query.subclassId?.let {
            conditions += "isc.subclass_id = ?"
            params += it
        }
        query.expansionId?.let {
            conditions += "v.expansion_id = ?"
            params += it
        }
        when (query.hasOverride) {
            true -> conditions += "o.id IS NOT NULL"
            false -> conditions += "o.id IS NULL"
            null -> Unit
        }

        val whereClause =
            if (conditions.isEmpty()) {
                ""
            } else {
                "WHERE ${conditions.joinToString(" AND ")}"
            }
        val orderBy = resolveSortClause(query.sort, query.localeColumnSuffix)
        val countSql =
            """
            SELECT COUNT(*)
            FROM v_item v
                LEFT JOIN locale nl ON nl.id = v.name_id
                LEFT JOIN item_subclass isc ON isc.internal_id = v.item_subclass_id AND isc.class_id = v.item_class_id
                LEFT JOIN item o ON o.id = v.id AND o.is_override = TRUE
            $whereClause
            """.trimIndent()
        val totalItems = jdbcTemplate.queryForObject(countSql, Long::class.java, *params.toTypedArray()) ?: 0L
        val totalPages = if (totalItems == 0L) 0 else ((totalItems + normalizedPageSize - 1) / normalizedPageSize).toInt()
        val offset = normalizedPage * normalizedPageSize

        val selectSql =
            """
            SELECT
                v.id,
                v.name_id,
                v.quality_id,
                iq.type AS quality_type,
                COALESCE(iql.${query.localeColumnSuffix}, iql.en_gb, iql.en_us) AS quality_name,
                v.item_class_id,
                COALESCE(icl.${query.localeColumnSuffix}, icl.en_gb, icl.en_us) AS item_class_name,
                v.item_subclass_id,
                COALESCE(iscl.${query.localeColumnSuffix}, iscl.en_gb, iscl.en_us) AS item_subclass_name,
                v.expansion_id,
                COALESCE(el.${query.localeColumnSuffix}, el.en_gb, el.en_us, e.slug) AS expansion_name,
                v.level,
                v.required_level,
                v.max_count,
                v.media_url,
                v.media_source_url,
                v.purchase_price,
                v.purchase_quantity,
                v.sell_price,
                v.binding_id,
                v.inventory_type_id,
                v.is_equippable,
                v.is_stackable,
                o.override_note,
                COALESCE(o.created_at, b.created_at) AS created_at,
                COALESCE(o.updated_at, b.updated_at) AS updated_at,
                o.id IS NOT NULL AS has_override,
                b.id IS NOT NULL AS has_base,
                COALESCE(nl.${query.localeColumnSuffix}, nl.en_gb, nl.en_us) AS item_name,
                nl.en_us, nl.en_gb, nl.de_de, nl.es_es, nl.es_mx, nl.fr_fr, nl.it_it,
                nl.ko_kr, nl.pt_br, nl.pt_pt, nl.ru_ru, nl.zh_cn, nl.zh_tw
            FROM v_item v
                LEFT JOIN locale nl ON nl.id = v.name_id
                LEFT JOIN item_quality iq ON iq.internal_id = v.quality_id
                LEFT JOIN locale iql ON iql.id = iq.name_id
                LEFT JOIN item_class ic ON ic.id = v.item_class_id
                LEFT JOIN locale icl ON icl.id = ic.name_id
                LEFT JOIN item_subclass isc ON isc.internal_id = v.item_subclass_id AND isc.class_id = v.item_class_id
                LEFT JOIN locale iscl ON iscl.id = isc.display_name_id
                LEFT JOIN expansion e ON e.id = v.expansion_id
                LEFT JOIN locale el ON el.id = e.name_id
                LEFT JOIN item o ON o.id = v.id AND o.is_override = TRUE
                LEFT JOIN item b ON b.id = v.id AND b.is_override = FALSE
            $whereClause
            $orderBy
            LIMIT ? OFFSET ?
            """.trimIndent()

        val listParams = params.toMutableList()
        listParams += normalizedPageSize
        listParams += offset
        val items =
            jdbcTemplate.query(selectSql, { rs, _ -> rs.toAdminItemListRow() }, *listParams.toTypedArray())

        return AdminItemPage(
            items = items,
            page =
                PageMetadata(
                    page = normalizedPage,
                    pageSize = normalizedPageSize,
                    totalItems = totalItems,
                    totalPages = totalPages,
                ),
        )
    }

    fun findAdminItem(
        itemId: Int,
        includeBase: Boolean = false,
        includeOverride: Boolean = false,
        localeColumnSuffix: String = AdminExpansionRepository.DEFAULT_LOCALE_COLUMN_SUFFIX,
    ): AdminItem? {
        val page =
            search(
                AdminItemSearchQuery(
                    page = 0,
                    pageSize = 1,
                    itemId = itemId,
                    localeColumnSuffix = localeColumnSuffix,
                ),
            )
        val item = page.items.firstOrNull() ?: return null
        val baseRow = if (includeBase) findItemRow(itemId, isOverride = false) else null
        val overrideRow = if (includeOverride) findItemRow(itemId, isOverride = true) else null
        return item.copy(
            base = baseRow?.let { rowToSnapshot(it) },
            `override` = overrideRow?.let { rowToSnapshot(it) },
        )
    }

    fun upsertOverride(
        itemId: Int,
        request: AdminItemOverrideRequest,
        requireBase: Boolean = true,
    ): AdminItem {
        if (requireBase && !itemExists(itemId)) {
            error("Item not found: $itemId")
        }
        val existing = findItemRow(itemId, isOverride = true)
        val merged = mergeOverrideRequest(existing, request, itemId)
        if (existing == null) {
            insertItemRow(merged)
        } else {
            updateItemRow(merged)
        }
        return findAdminItem(itemId) ?: error("Item $itemId was not found after override upsert")
    }

    fun createOverrideOnly(
        itemId: Int,
        request: AdminItemOverrideRequest,
    ): AdminItem {
        if (itemExists(itemId)) {
            error("Item already exists: $itemId")
        }
        val row = mergeOverrideRequest(existing = null, request = request, itemId = itemId, requireAllFields = true)
        insertItemRow(row)
        return findAdminItem(itemId) ?: error("Item $itemId was not found after create")
    }

    fun deleteOverride(itemId: Int): Boolean {
        val deleted =
            jdbcTemplate.update(
                "DELETE FROM `item` WHERE id = ? AND is_override = TRUE",
                itemId,
            )
        if (deleted > 0) {
            localeJdbcRepository.findLocaleId(
                LocaleSourceType.ITEM,
                overrideLocaleKey(itemId),
                "name",
            )?.let(localeJdbcRepository::deleteById)
        }
        return deleted > 0
    }

    fun findItemRow(
        itemId: Int,
        isOverride: Boolean,
    ): ItemRow? =
        jdbcTemplate
            .query(
                """
                SELECT
                    id, is_override, name_id, quality_id, level, max_count, media_url, media_source_url,
                    purchase_price, purchase_quantity, required_level, sell_price, binding_id,
                    inventory_type_id, item_class_id, item_subclass_id, expansion_id,
                    is_equippable, is_stackable, override_note, created_at, updated_at
                FROM `item`
                WHERE id = ? AND is_override = ?
                """.trimIndent(),
                { rs, _ -> rs.toItemRow() },
                itemId,
                isOverride,
            ).firstOrNull()

    fun findEffectiveRow(itemId: Int): ItemRow? =
        jdbcTemplate
            .query(
                """
                SELECT
                    v.id AS id,
                    FALSE AS is_override,
                    v.name_id, v.quality_id, v.level, v.max_count, v.media_url, v.media_source_url,
                    v.purchase_price, v.purchase_quantity, v.required_level, v.sell_price, v.binding_id,
                    v.inventory_type_id, v.item_class_id, v.item_subclass_id, v.expansion_id,
                    v.is_equippable, v.is_stackable,
                    o.override_note,
                    COALESCE(o.created_at, b.created_at) AS created_at,
                    COALESCE(o.updated_at, b.updated_at) AS updated_at
                FROM v_item v
                    LEFT JOIN item o ON o.id = v.id AND o.is_override = TRUE
                    LEFT JOIN item b ON b.id = v.id AND b.is_override = FALSE
                WHERE v.id = ?
                """.trimIndent(),
                { rs, _ -> rs.toItemRow() },
                itemId,
            ).firstOrNull()

    private fun mergeOverrideRequest(
        existing: ItemRow?,
        request: AdminItemOverrideRequest,
        itemId: Int,
        requireAllFields: Boolean = false,
    ): ItemRow {
        val nameId =
            when {
                request.nameLocales != null -> {
                    localeJdbcRepository.upsert(
                        LocaleSourceType.ITEM,
                        overrideLocaleKey(itemId),
                        "name",
                        request.nameLocales.toLocaleDTO(),
                    )
                }
                existing?.nameId != null -> existing.nameId
                requireAllFields -> error("nameLocales is required for override-only items")
                else -> null
            }

        fun <T> pick(
            requestValue: T?,
            existingValue: T?,
            required: Boolean = false,
            fieldName: String = "field",
        ): T? =
            when {
                requestValue != null -> requestValue
                existing != null -> existingValue
                requireAllFields && required -> error("$fieldName is required for override-only items")
                else -> null
            }

        return ItemRow(
            id = itemId,
            isOverride = true,
            nameId = nameId,
            qualityId = pick(request.qualityId, existing?.qualityId, requireAllFields, "qualityId"),
            level = pick(request.level, existing?.level, requireAllFields, "level"),
            maxCount = pick(request.maxCount, existing?.maxCount, requireAllFields, "maxCount"),
            mediaUrl = pick(request.mediaUrl, existing?.mediaUrl),
            mediaSourceUrl = pick(request.mediaSourceUrl, existing?.mediaSourceUrl),
            purchasePrice = pick(request.purchasePrice, existing?.purchasePrice, requireAllFields, "purchasePrice"),
            purchaseQuantity = pick(request.purchaseQuantity, existing?.purchaseQuantity, requireAllFields, "purchaseQuantity"),
            requiredLevel = pick(request.requiredLevel, existing?.requiredLevel, requireAllFields, "requiredLevel"),
            sellPrice = pick(request.sellPrice, existing?.sellPrice, requireAllFields, "sellPrice"),
            bindingId = pick(request.bindingId, existing?.bindingId),
            inventoryTypeId = pick(request.inventoryTypeId, existing?.inventoryTypeId),
            itemClassId = pick(request.itemClassId, existing?.itemClassId, requireAllFields, "itemClassId"),
            itemSubclassId = pick(request.itemSubclassId, existing?.itemSubclassId, requireAllFields, "itemSubclassId"),
            expansionId = pick(request.expansionId, existing?.expansionId),
            isEquippable = pick(request.isEquippable, existing?.isEquippable, requireAllFields, "isEquippable"),
            isStackable = pick(request.isStackable, existing?.isStackable, requireAllFields, "isStackable"),
            overrideNote = request.overrideNote ?: existing?.overrideNote,
            createdAt = existing?.createdAt,
            updatedAt = null,
        )
    }

    private fun insertItemRow(row: ItemRow) {
        jdbcTemplate.update(
            """
            INSERT INTO `item` (
                id, is_override, name_id, quality_id, level, max_count, media_url, media_source_url,
                purchase_price, purchase_quantity, required_level, sell_price, binding_id,
                inventory_type_id, item_class_id, item_subclass_id, expansion_id,
                is_equippable, is_stackable, override_note
            ) VALUES (?, TRUE, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            row.id,
            row.nameId,
            row.qualityId,
            row.level,
            row.maxCount,
            row.mediaUrl,
            row.mediaSourceUrl,
            row.purchasePrice,
            row.purchaseQuantity,
            row.requiredLevel,
            row.sellPrice,
            row.bindingId,
            row.inventoryTypeId,
            row.itemClassId,
            row.itemSubclassId,
            row.expansionId,
            row.isEquippable,
            row.isStackable,
            row.overrideNote,
        )
    }

    private fun updateItemRow(row: ItemRow) {
        jdbcTemplate.update(
            """
            UPDATE `item`
            SET name_id = ?,
                quality_id = ?,
                level = ?,
                max_count = ?,
                media_url = ?,
                media_source_url = ?,
                purchase_price = ?,
                purchase_quantity = ?,
                required_level = ?,
                sell_price = ?,
                binding_id = ?,
                inventory_type_id = ?,
                item_class_id = ?,
                item_subclass_id = ?,
                expansion_id = ?,
                is_equippable = ?,
                is_stackable = ?,
                override_note = ?
            WHERE id = ? AND is_override = TRUE
            """.trimIndent(),
            row.nameId,
            row.qualityId,
            row.level,
            row.maxCount,
            row.mediaUrl,
            row.mediaSourceUrl,
            row.purchasePrice,
            row.purchaseQuantity,
            row.requiredLevel,
            row.sellPrice,
            row.bindingId,
            row.inventoryTypeId,
            row.itemClassId,
            row.itemSubclassId,
            row.expansionId,
            row.isEquippable,
            row.isStackable,
            row.overrideNote,
            row.id,
        )
    }

    private fun rowToSnapshot(row: ItemRow): AdminItemOverrideSnapshot {
        val nameLocales =
            row.nameId?.let { nameId ->
                localeJdbcRepository.findById(nameId)?.toGameLocale()
            }
        return AdminItemOverrideSnapshot(
            nameLocales = nameLocales,
            qualityId = row.qualityId,
            itemClassId = row.itemClassId,
            itemSubclassId = row.itemSubclassId,
            expansionId = row.expansionId,
            level = row.level,
            requiredLevel = row.requiredLevel,
            maxCount = row.maxCount,
            mediaUrl = row.mediaUrl,
            mediaSourceUrl = row.mediaSourceUrl,
            purchasePrice = row.purchasePrice,
            purchaseQuantity = row.purchaseQuantity,
            sellPrice = row.sellPrice,
            bindingId = row.bindingId,
            inventoryTypeId = row.inventoryTypeId,
            isEquippable = row.isEquippable,
            isStackable = row.isStackable,
            overrideNote = row.overrideNote,
        )
    }

    private fun resolveSortClause(
        sort: String,
        localeColumnSuffix: String,
    ): String =
        when (sort) {
            "name" -> "ORDER BY item_name ASC, v.id ASC"
            "quality" -> "ORDER BY quality_name ASC, v.id ASC"
            "expansion" -> "ORDER BY expansion_name ASC, v.id ASC"
            "updatedAt" -> "ORDER BY COALESCE(o.updated_at, b.updated_at) DESC, v.id ASC"
            else -> "ORDER BY v.id ASC"
        }

    companion object {
        fun overrideLocaleKey(itemId: Int): String = localeSourceKey("override", itemId)
    }
}

private fun ResultSet.toAdminItemListRow(): AdminItem {
    val locale = toLocaleDTO()
    return AdminItem(
        id = getInt("id"),
        hasOverride = getBoolean("has_override"),
        hasBase = getBoolean("has_base"),
        name = getString("item_name"),
        nameLocales = locale.toGameLocale(),
        qualityId = getNullableLong("quality_id"),
        qualityType = getString("quality_type"),
        qualityName = getString("quality_name"),
        itemClassId = getNullableInt("item_class_id"),
        itemClassName = getString("item_class_name"),
        itemSubclassId = getNullableLong("item_subclass_id"),
        itemSubclassName = getString("item_subclass_name"),
        expansionId = getNullableInt("expansion_id"),
        expansionName = getString("expansion_name"),
        level = getNullableInt("level"),
        requiredLevel = getNullableInt("required_level"),
        maxCount = getNullableInt("max_count"),
        mediaUrl = getString("media_url"),
        mediaSourceUrl = getString("media_source_url"),
        purchasePrice = getNullableInt("purchase_price"),
        purchaseQuantity = getNullableInt("purchase_quantity"),
        sellPrice = getNullableInt("sell_price"),
        bindingId = getNullableLong("binding_id"),
        inventoryTypeId = getNullableLong("inventory_type_id"),
        isEquippable = getNullableBoolean("is_equippable"),
        isStackable = getNullableBoolean("is_stackable"),
        overrideNote = getString("override_note"),
        createdAt = getTimestamp("created_at")?.toOffsetDateTime(),
        updatedAt = getTimestamp("updated_at")?.toOffsetDateTime(),
    )
}

private fun ResultSet.toItemRow(): ItemRow =
    ItemRow(
        id = getInt("id"),
        isOverride = getBoolean("is_override"),
        nameId = getNullableLong("name_id"),
        qualityId = getNullableLong("quality_id"),
        level = getNullableInt("level"),
        maxCount = getNullableInt("max_count"),
        mediaUrl = getString("media_url"),
        mediaSourceUrl = getString("media_source_url"),
        purchasePrice = getNullableInt("purchase_price"),
        purchaseQuantity = getNullableInt("purchase_quantity"),
        requiredLevel = getNullableInt("required_level"),
        sellPrice = getNullableInt("sell_price"),
        bindingId = getNullableLong("binding_id"),
        inventoryTypeId = getNullableLong("inventory_type_id"),
        itemClassId = getNullableInt("item_class_id"),
        itemSubclassId = getNullableLong("item_subclass_id"),
        expansionId = getNullableInt("expansion_id"),
        isEquippable = getNullableBoolean("is_equippable"),
        isStackable = getNullableBoolean("is_stackable"),
        overrideNote = getString("override_note"),
        createdAt = getTimestamp("created_at")?.toOffsetDateTime(),
        updatedAt = getTimestamp("updated_at")?.toOffsetDateTime(),
    )

private fun ResultSet.getNullableBoolean(column: String): Boolean? {
    val value = getBoolean(column)
    return if (wasNull()) null else value
}

private fun Timestamp.toOffsetDateTime(): OffsetDateTime = toInstant().atOffset(ZoneOffset.UTC)
