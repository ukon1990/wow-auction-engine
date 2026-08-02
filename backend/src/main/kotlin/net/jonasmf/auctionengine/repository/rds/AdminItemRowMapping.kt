package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.generated.model.AdminExpansion
import net.jonasmf.auctionengine.generated.model.AdminItemFields
import net.jonasmf.auctionengine.generated.model.AdminItemReference
import net.jonasmf.auctionengine.generated.model.GameLocale
import net.jonasmf.auctionengine.mapper.toGameLocale
import net.jonasmf.auctionengine.mapper.toLocaleDTO
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal fun ResultSet.toAdminItemFields(): AdminItemFields =
    AdminItemFields(
        id = getInt("id"), name = getString("item_name"),
        nameLocales = nullableLong("name_id")?.let { toLocaleDTO().toGameLocale() },
        quality = reference("quality_id", "quality_name", "quality_type"), level = nullableInt("level"),
        rank = nullableInt("rank"), requiredLevel = nullableInt("required_level"), mediaUrl = getString("media_url"),
        mediaSourceUrl = getString("media_source_url"), itemClass = reference("item_class_id", "item_class_name"),
        itemSubclass = reference("item_subclass_id", "item_subclass_name"),
        inventoryType = reference("inventory_type_id", "inventory_type_name", "inventory_type"),
        binding = reference("binding_id", "binding_name", "binding_type"), purchasePrice = nullableInt("purchase_price"),
        sellPrice = nullableInt("sell_price"), maxCount = nullableInt("max_count"), isEquippable = nullableBoolean("is_equippable"),
        isStackable = nullableBoolean("is_stackable"), purchaseQuantity = nullableInt("purchase_quantity"), expansion = expansion(),
        overrideNote = getString("override_note"), createdAt = nullableTimestamp("created_at")?.toOffsetDateTime(),
        updatedAt = nullableTimestamp("updated_at")?.toOffsetDateTime(),
    )

private fun ResultSet.reference(idColumn: String, nameColumn: String, typeColumn: String? = null): AdminItemReference? {
    val id = nullableLong(idColumn) ?: return null
    return AdminItemReference(id = id, name = getString(nameColumn), type = typeColumn?.let { getString(it) })
}

private fun ResultSet.expansion(): AdminExpansion? {
    val id = nullableInt("expansion_id") ?: return null
    return AdminExpansion(
        id = id, slug = getString("expansion_slug"), name = getString("expansion_name"),
        nameLocales = GameLocale(enUS = getString("expansion_en_us"), enGB = getString("expansion_en_gb"), deDE = getString("expansion_de_de"), esES = getString("expansion_es_es"), esMX = getString("expansion_es_mx"), frFR = getString("expansion_fr_fr"), itIT = getString("expansion_it_it"), koKR = getString("expansion_ko_kr"), ptBR = getString("expansion_pt_br"), ptPT = getString("expansion_pt_pt"), ruRU = getString("expansion_ru_ru"), zhCN = getString("expansion_zh_cn"), zhTW = getString("expansion_zh_tw")),
        majorVersion = getInt("expansion_major_version"), displayOrder = getInt("expansion_display_order"),
    )
}

private fun ResultSet.nullableInt(column: String): Int? { val value = getInt(column); return if (wasNull()) null else value }
private fun ResultSet.nullableLong(column: String): Long? { val value = getLong(column); return if (wasNull()) null else value }
private fun ResultSet.nullableBoolean(column: String): Boolean? { val value = getBoolean(column); return if (wasNull()) null else value }
private fun ResultSet.nullableTimestamp(column: String): Timestamp? = getTimestamp(column)
private fun Timestamp.toOffsetDateTime(): OffsetDateTime = toInstant().atOffset(ZoneOffset.UTC)
