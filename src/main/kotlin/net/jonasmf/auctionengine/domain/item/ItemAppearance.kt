package net.jonasmf.auctionengine.domain.item

data class ItemAppearance(
    val id: Int,
    val slot: InventoryType,
    val itemClass: ItemClass,
    val itemSubclass: ItemSubclass,
    val itemDisplayInfoId: Int,
    val items: List<ItemSummary> = emptyList(),
    val mediaUrl: String,
)
