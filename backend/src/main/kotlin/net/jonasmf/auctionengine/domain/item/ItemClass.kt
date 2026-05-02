package net.jonasmf.auctionengine.domain.item

import net.jonasmf.auctionengine.dto.LocaleDTO

data class ItemClass(
    val id: Int,
    val name: LocaleDTO,
    val itemSubclasses: List<ItemSubclass> = emptyList(),
)

data class ItemSubclass(
    val classId: Int,
    val subclassId: Int,
    val displayName: LocaleDTO,
    val hideSubclassInTooltips: Boolean? = null,
)
