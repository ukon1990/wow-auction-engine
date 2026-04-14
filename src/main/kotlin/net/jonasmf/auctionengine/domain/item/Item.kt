package net.jonasmf.auctionengine.domain.item

import net.jonasmf.auctionengine.dto.LocaleDTO

data class ItemQuality(
    val type: String,
    val name: LocaleDTO,
)

data class InventoryType(
    val type: String,
    val name: LocaleDTO,
)

data class ItemAppearanceReference(
    val id: Int,
    val href: String,
)

data class ItemSummary(
    val id: Int,
    val name: LocaleDTO,
    val href: String? = null,
)

data class Item(
    val id: Int,
    val name: LocaleDTO,
    val quality: ItemQuality,
    val level: Int,
    val requiredLevel: Int,
    val mediaUrl: String,
    val itemClass: ItemClass,
    val itemSubclass: ItemSubclass,
    val inventoryType: InventoryType,
    val purchasePrice: Int,
    val sellPrice: Int,
    val maxCount: Int,
    val isEquippable: Boolean,
    val isStackable: Boolean,
    val purchaseQuantity: Int,
    val appearances: List<ItemAppearanceReference> = emptyList(),
)
