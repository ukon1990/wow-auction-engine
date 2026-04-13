package net.jonasmf.auctionengine.domain.profession

import net.jonasmf.auctionengine.dto.LocaleDTO

data class ModifiedCraftingCategory(
    val id: Int,
    val name: LocaleDTO,
)

data class ModifiedCraftingSlot(
    val id: Int,
    val description: LocaleDTO,
    val compatibleCategories: List<ModifiedCraftingCategory> = emptyList(),
    val displayOrder: Int? = null,
)

data class Recipe(
    val id: Int,
    val name: LocaleDTO,
    val description: LocaleDTO? = null,
    val mediaUrl: String? = null,
    val rank: Int? = null,
    val modifiedCraftingSlots: List<ModifiedCraftingSlot> = emptyList(),
)
