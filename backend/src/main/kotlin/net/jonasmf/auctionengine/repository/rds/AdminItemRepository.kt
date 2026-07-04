package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.generated.model.AdminExpansion1
import net.jonasmf.auctionengine.generated.model.AdminItem1
import net.jonasmf.auctionengine.generated.model.AdminItemCreateRequest
import net.jonasmf.auctionengine.generated.model.AdminItemFields
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemReference
import net.jonasmf.auctionengine.generated.model.GameLocale
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.mapper.toGameLocale
import net.jonasmf.auctionengine.mapper.toLocaleDTO
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.ceil

data class AdminItemSearchResult(
    val items: List<AdminItem1>,
    val totalItems: Long,
)

data class AdminItemRows(
    val effective: AdminItemFields,
    val base: AdminItemFields?,
    val override: AdminItemFields?,
) {
    fun toAdminItem(
        includeBase: Boolean,
        includeOverride: Boolean,
    ): AdminItem1 =
        AdminItem1(
            id = effective.id ?: error("Effective item id is missing"),
            hasBase = base != null,
            hasOverride = override != null,
            effective = effective,
            base = base.takeIf { includeBase },
            `override` = override.takeIf { includeOverride },
        )
}

interface AdminItemRepositoryPort {
    fun searchItems(
        query: String?,
        hasBase: Boolean?,
        hasOverride: Boolean?,
        page: Int,
        pageSize: Int,
        localeColumnSuffix: String,
    ): AdminItemSearchResult

    fun pageMetadata(
        page: Int,
        pageSize: Int,
        totalItems: Long,
    ): PageMetadata

    fun findItemRows(
        id: Int,
        localeColumnSuffix: String,
    ): AdminItemRows?

    fun hasAnyItemRow(id: Int): Boolean

    fun hasBaseItem(id: Int): Boolean

    fun hasOverrideItem(id: Int): Boolean

    fun qualityId(type: String): Long?

    fun inventoryTypeId(type: String): Long?

    fun bindingId(type: String): Long?

    fun itemClassExists(id: Int): Boolean

    fun itemSubclassInternalId(
        classId: Int,
        subclassId: Int,
    ): Long?

    fun expansionExists(expansionId: Int): Boolean

    fun upsertOverride(
        id: Int,
        request: AdminItemOverrideRequest,
        itemSubclassInternalId: Long?,
    )

    fun createOverrideOnly(
        request: AdminItemCreateRequest,
        itemSubclassInternalId: Long,
    )

    fun deleteOverride(id: Int): Boolean
}

@Repository
class AdminItemRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val localeJdbcRepository: LocaleJdbcRepository,
) : AdminItemRepositoryPort {
    override fun searchItems(
        query: String?,
        hasBase: Boolean?,
        hasOverride: Boolean?,
        page: Int,
        pageSize: Int,
        localeColumnSuffix: String,
    ): AdminItemSearchResult {
        val params = mutableListOf<Any?>()
        val whereSql = itemSearchWhereSql(query, hasBase, hasOverride, localeColumnSuffix, params)
        val count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM v_item i
                    LEFT JOIN `item` base_item ON base_item.id = i.id AND base_item.is_override = FALSE
                    LEFT JOIN `item` override_item ON override_item.id = i.id AND override_item.is_override = TRUE
                    LEFT JOIN locale name_l ON name_l.id = i.name_id
                $whereSql
                """.trimIndent(),
                Long::class.java,
                *params.toTypedArray(),
            ) ?: 0

        val offset = (page - 1) * pageSize
        val itemParams = params.toMutableList()
        itemParams += pageSize
        itemParams += offset
        val items =
            jdbcTemplate.query(
                """
                ${itemSelectSql("v_item", localeColumnSuffix)}
                $whereSql
                ORDER BY i.id
                LIMIT ? OFFSET ?
                """.trimIndent(),
                { rs, _ ->
                    AdminItem1(
                        id = rs.getInt("id"),
                        hasBase = rs.getBoolean("has_base"),
                        hasOverride = rs.getBoolean("has_override"),
                        effective = rs.toAdminItemFields(),
                    )
                },
                *itemParams.toTypedArray(),
            )
        return AdminItemSearchResult(items = items, totalItems = count)
    }

    override fun pageMetadata(
        page: Int,
        pageSize: Int,
        totalItems: Long,
    ): PageMetadata =
        PageMetadata(
            page = page,
            pageSize = pageSize,
            totalItems = totalItems,
            totalPages = if (totalItems == 0L) 0 else ceil(totalItems.toDouble() / pageSize).toInt(),
        )

    override fun findItemRows(
        id: Int,
        localeColumnSuffix: String,
    ): AdminItemRows? {
        val effective = findFields("v_item", "i.id = ?", localeColumnSuffix, id) ?: return null
        return AdminItemRows(
            effective = effective,
            base = findFields("`item`", "i.id = ? AND i.is_override = FALSE", localeColumnSuffix, id),
            override = findFields("`item`", "i.id = ? AND i.is_override = TRUE", localeColumnSuffix, id),
        )
    }

    override fun hasAnyItemRow(id: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `item` WHERE id = ?",
            Long::class.java,
            id,
        )!! > 0

    override fun hasBaseItem(id: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `item` WHERE id = ? AND is_override = FALSE",
            Long::class.java,
            id,
        )!! > 0

    override fun hasOverrideItem(id: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `item` WHERE id = ? AND is_override = TRUE",
            Long::class.java,
            id,
        )!! > 0

    override fun qualityId(type: String): Long? = lookupIdByType("item_quality", type)

    override fun inventoryTypeId(type: String): Long? = lookupIdByType("inventory_type", type)

    override fun bindingId(type: String): Long? = lookupIdByType("item_binding", type)

    override fun itemClassExists(id: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM item_class WHERE id = ?",
            Long::class.java,
            id,
        )!! > 0

    override fun itemSubclassInternalId(
        classId: Int,
        subclassId: Int,
    ): Long? =
        jdbcTemplate
            .query(
                "SELECT internal_id FROM item_subclass WHERE class_id = ? AND subclass_id = ?",
                { rs, _ -> rs.getLong("internal_id") },
                classId,
                subclassId,
            ).firstOrNull()

    override fun expansionExists(expansionId: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expansion WHERE id = ?",
            Long::class.java,
            expansionId,
        )!! > 0

    override fun upsertOverride(
        id: Int,
        request: AdminItemOverrideRequest,
        itemSubclassInternalId: Long?,
    ) {
        val nameId =
            request.nameLocales?.let {
                localeJdbcRepository.upsert(
                    LocaleSourceType.ITEM,
                    overrideLocaleSourceKey(id),
                    "name",
                    it.toLocaleDTO(),
                )
            }
        jdbcTemplate.update(
            """
            INSERT INTO `item` (
                id,
                is_override,
                name_id,
                quality_id,
                level,
                required_level,
                media_url,
                media_source_url,
                item_class_id,
                item_subclass_id,
                inventory_type_id,
                binding_id,
                purchase_price,
                sell_price,
                max_count,
                is_equippable,
                is_stackable,
                purchase_quantity,
                expansion_id,
                override_note
            ) VALUES (?, TRUE, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name_id = VALUES(name_id),
                quality_id = VALUES(quality_id),
                level = VALUES(level),
                required_level = VALUES(required_level),
                media_url = VALUES(media_url),
                media_source_url = VALUES(media_source_url),
                item_class_id = VALUES(item_class_id),
                item_subclass_id = VALUES(item_subclass_id),
                inventory_type_id = VALUES(inventory_type_id),
                binding_id = VALUES(binding_id),
                purchase_price = VALUES(purchase_price),
                sell_price = VALUES(sell_price),
                max_count = VALUES(max_count),
                is_equippable = VALUES(is_equippable),
                is_stackable = VALUES(is_stackable),
                purchase_quantity = VALUES(purchase_quantity),
                expansion_id = VALUES(expansion_id),
                override_note = VALUES(override_note)
            """.trimIndent(),
            id,
            nameId,
            request.qualityType?.let { qualityId(it) },
            request.level,
            request.requiredLevel,
            request.mediaUrl,
            request.mediaSourceUrl,
            request.itemClassId,
            itemSubclassInternalId,
            request.inventoryType?.let { inventoryTypeId(it) },
            request.bindingType?.let { bindingId(it) },
            request.purchasePrice,
            request.sellPrice,
            request.maxCount,
            request.isEquippable,
            request.isStackable,
            request.purchaseQuantity,
            request.expansionId,
            request.overrideNote,
        )
    }

    override fun createOverrideOnly(
        request: AdminItemCreateRequest,
        itemSubclassInternalId: Long,
    ) {
        upsertOverride(
            request.id,
            AdminItemOverrideRequest(
                nameLocales = request.nameLocales,
                qualityType = request.qualityType,
                level = request.level,
                requiredLevel = request.requiredLevel,
                mediaUrl = request.mediaUrl,
                mediaSourceUrl = request.mediaSourceUrl,
                itemClassId = request.itemClassId,
                itemSubclassId = request.itemSubclassId,
                inventoryType = request.inventoryType,
                bindingType = request.bindingType,
                purchasePrice = request.purchasePrice,
                sellPrice = request.sellPrice,
                maxCount = request.maxCount,
                isEquippable = request.isEquippable,
                isStackable = request.isStackable,
                purchaseQuantity = request.purchaseQuantity,
                expansionId = request.expansionId,
                overrideNote = request.overrideNote,
            ),
            itemSubclassInternalId,
        )
    }

    override fun deleteOverride(id: Int): Boolean =
        jdbcTemplate.update("DELETE FROM `item` WHERE id = ? AND is_override = TRUE", id) > 0

    private fun lookupIdByType(
        tableName: String,
        type: String,
    ): Long? =
        jdbcTemplate
            .query(
                "SELECT internal_id FROM $tableName WHERE type = ?",
                { rs, _ -> rs.getLong("internal_id") },
                type,
            ).firstOrNull()

    private fun findFields(
        tableName: String,
        whereSql: String,
        localeColumnSuffix: String,
        vararg params: Any?,
    ): AdminItemFields? =
        jdbcTemplate
            .query(
                """
                ${itemSelectSql(tableName, localeColumnSuffix)}
                WHERE $whereSql
                """.trimIndent(),
                { rs, _ -> rs.toAdminItemFields() },
                *params,
            ).firstOrNull()

    private fun itemSearchWhereSql(
        query: String?,
        hasBase: Boolean?,
        hasOverride: Boolean?,
        localeColumnSuffix: String,
        params: MutableList<Any?>,
    ): String {
        val clauses = mutableListOf<String>()
        val normalizedQuery = query?.trim().orEmpty()
        if (normalizedQuery.isNotEmpty()) {
            normalizedQuery.toIntOrNull()?.let { itemId ->
                clauses += "i.id = ?"
                params += itemId
            } ?: run {
                val likeQuery = "%$normalizedQuery%"
                clauses +=
                    """
                    (
                        name_l.$localeColumnSuffix LIKE ?
                        OR name_l.en_gb LIKE ?
                        OR name_l.en_us LIKE ?
                    )
                    """.trimIndent()
                params += likeQuery
                params += likeQuery
                params += likeQuery
            }
        }
        if (hasBase != null) {
            clauses += if (hasBase) "base_item.id IS NOT NULL" else "base_item.id IS NULL"
        }
        if (hasOverride != null) {
            clauses += if (hasOverride) "override_item.id IS NOT NULL" else "override_item.id IS NULL"
        }
        return if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
    }

    private fun itemSelectSql(
        tableName: String,
        localeColumnSuffix: String,
    ): String =
        """
        SELECT
            i.id,
            base_item.id IS NOT NULL AS has_base,
            override_item.id IS NOT NULL AS has_override,
            i.name_id,
            COALESCE(name_l.$localeColumnSuffix, name_l.en_gb, name_l.en_us) AS item_name,
            name_l.en_us,
            name_l.en_gb,
            name_l.de_de,
            name_l.es_es,
            name_l.es_mx,
            name_l.fr_fr,
            name_l.it_it,
            name_l.ko_kr,
            name_l.pt_br,
            name_l.pt_pt,
            name_l.ru_ru,
            name_l.zh_cn,
            name_l.zh_tw,
            q.internal_id AS quality_id,
            q.type AS quality_type,
            COALESCE(q_l.$localeColumnSuffix, q_l.en_gb, q_l.en_us, q.type) AS quality_name,
            i.level,
            i.required_level,
            i.media_url,
            i.media_source_url,
            c.id AS item_class_id,
            COALESCE(c_l.$localeColumnSuffix, c_l.en_gb, c_l.en_us) AS item_class_name,
            sc.internal_id AS item_subclass_internal_id,
            sc.subclass_id AS item_subclass_id,
            COALESCE(sc_l.$localeColumnSuffix, sc_l.en_gb, sc_l.en_us) AS item_subclass_name,
            inv.internal_id AS inventory_type_id,
            inv.type AS inventory_type,
            COALESCE(inv_l.$localeColumnSuffix, inv_l.en_gb, inv_l.en_us, inv.type) AS inventory_type_name,
            b.internal_id AS binding_id,
            b.type AS binding_type,
            COALESCE(b_l.$localeColumnSuffix, b_l.en_gb, b_l.en_us, b.type) AS binding_name,
            i.purchase_price,
            i.sell_price,
            i.max_count,
            i.is_equippable,
            i.is_stackable,
            i.purchase_quantity,
            e.id AS expansion_id,
            e.slug AS expansion_slug,
            e.major_version AS expansion_major_version,
            e.display_order AS expansion_display_order,
            COALESCE(e_l.$localeColumnSuffix, e_l.en_gb, e_l.en_us, e.slug) AS expansion_name,
            e_l.en_us AS expansion_en_us,
            e_l.en_gb AS expansion_en_gb,
            e_l.de_de AS expansion_de_de,
            e_l.es_es AS expansion_es_es,
            e_l.es_mx AS expansion_es_mx,
            e_l.fr_fr AS expansion_fr_fr,
            e_l.it_it AS expansion_it_it,
            e_l.ko_kr AS expansion_ko_kr,
            e_l.pt_br AS expansion_pt_br,
            e_l.pt_pt AS expansion_pt_pt,
            e_l.ru_ru AS expansion_ru_ru,
            e_l.zh_cn AS expansion_zh_cn,
            e_l.zh_tw AS expansion_zh_tw,
            i.override_note,
            i.created_at,
            i.updated_at
        FROM $tableName i
            LEFT JOIN `item` base_item ON base_item.id = i.id AND base_item.is_override = FALSE
            LEFT JOIN `item` override_item ON override_item.id = i.id AND override_item.is_override = TRUE
            LEFT JOIN locale name_l ON name_l.id = i.name_id
            LEFT JOIN item_quality q ON q.internal_id = i.quality_id
            LEFT JOIN locale q_l ON q_l.id = q.name_id
            LEFT JOIN item_class c ON c.id = i.item_class_id
            LEFT JOIN locale c_l ON c_l.id = c.name_id
            LEFT JOIN item_subclass sc ON sc.internal_id = i.item_subclass_id
            LEFT JOIN locale sc_l ON sc_l.id = sc.display_name_id
            LEFT JOIN inventory_type inv ON inv.internal_id = i.inventory_type_id
            LEFT JOIN locale inv_l ON inv_l.id = inv.name_id
            LEFT JOIN item_binding b ON b.internal_id = i.binding_id
            LEFT JOIN locale b_l ON b_l.id = b.name_id
            LEFT JOIN expansion e ON e.id = i.expansion_id
            LEFT JOIN locale e_l ON e_l.id = e.name_id
        """.trimIndent()
}

fun overrideLocaleSourceKey(id: Int): String = "override:$id"

private fun ResultSet.toAdminItemFields(): AdminItemFields =
    AdminItemFields(
        id = getInt("id"),
        name = getString("item_name"),
        nameLocales = nullableLong("name_id")?.let { toLocaleDTO().toGameLocale() },
        quality = reference("quality_id", "quality_name", "quality_type"),
        level = nullableInt("level"),
        requiredLevel = nullableInt("required_level"),
        mediaUrl = getString("media_url"),
        mediaSourceUrl = getString("media_source_url"),
        itemClass = reference("item_class_id", "item_class_name"),
        itemSubclass = reference("item_subclass_id", "item_subclass_name"),
        inventoryType = reference("inventory_type_id", "inventory_type_name", "inventory_type"),
        binding = reference("binding_id", "binding_name", "binding_type"),
        purchasePrice = nullableInt("purchase_price"),
        sellPrice = nullableInt("sell_price"),
        maxCount = nullableInt("max_count"),
        isEquippable = nullableBoolean("is_equippable"),
        isStackable = nullableBoolean("is_stackable"),
        purchaseQuantity = nullableInt("purchase_quantity"),
        expansion = expansion(),
        overrideNote = getString("override_note"),
        createdAt = nullableTimestamp("created_at")?.toOffsetDateTime(),
        updatedAt = nullableTimestamp("updated_at")?.toOffsetDateTime(),
    )

private fun ResultSet.reference(
    idColumn: String,
    nameColumn: String,
    typeColumn: String? = null,
): AdminItemReference? {
    val id = nullableLong(idColumn) ?: return null
    return AdminItemReference(
        id = id,
        name = getString(nameColumn),
        type = typeColumn?.let { getString(it) },
    )
}

private fun ResultSet.expansion(): AdminExpansion1? {
    val id = nullableInt("expansion_id") ?: return null
    return AdminExpansion1(
        id = id,
        slug = getString("expansion_slug"),
        name = getString("expansion_name"),
        nameLocales =
            GameLocale(
                enUS = getString("expansion_en_us"),
                enGB = getString("expansion_en_gb"),
                deDE = getString("expansion_de_de"),
                esES = getString("expansion_es_es"),
                esMX = getString("expansion_es_mx"),
                frFR = getString("expansion_fr_fr"),
                itIT = getString("expansion_it_it"),
                koKR = getString("expansion_ko_kr"),
                ptBR = getString("expansion_pt_br"),
                ptPT = getString("expansion_pt_pt"),
                ruRU = getString("expansion_ru_ru"),
                zhCN = getString("expansion_zh_cn"),
                zhTW = getString("expansion_zh_tw"),
            ),
        majorVersion = getInt("expansion_major_version"),
        displayOrder = getInt("expansion_display_order"),
    )
}

private fun ResultSet.nullableInt(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

private fun ResultSet.nullableLong(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

private fun ResultSet.nullableBoolean(column: String): Boolean? {
    val value = getBoolean(column)
    return if (wasNull()) null else value
}

private fun ResultSet.nullableTimestamp(column: String): Timestamp? = getTimestamp(column)

private fun Timestamp.toOffsetDateTime(): OffsetDateTime = toInstant().atOffset(ZoneOffset.UTC)
