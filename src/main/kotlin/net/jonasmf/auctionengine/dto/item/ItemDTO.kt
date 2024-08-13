package net.jonasmf.auctionengine.dto.item

import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.dto.MediaDTO

data class Quality(
    val type: String,
    val name: LocaleDTO
)

data class ItemClass(
    val id: Int,
    val key: Href,
    val name: LocaleDTO,
)
data class ItemSubclass(
    val id: Int,
    val name: LocaleDTO,
    val key: Href
)

data class InventoryType(
    val type: String,
    val name: LocaleDTO
)

data class ItemDTO(
    val _links: Links,
    val id: Int,
    val name: LocaleDTO,
    val quality: Quality,
    val level: Int,
    val required_level: Int,
    val media: MediaDTO,
    val item_class: ItemClass,
    val item_subclass: ItemSubclass,
    val inventory_type: InventoryType,
    val purchase_price: Int,
    val sell_price: Int,
    val max_count: Int,
    val is_equippable: Boolean,
    val is_stackable: Boolean,
    val preview_item: ItemPreviewDTO,
    val purchase_quantity: Int,
    val appearances: List<Appearance>
)