package net.jonasmf.auctionengine.domain.profession

import net.jonasmf.auctionengine.dto.LocaleDTO
import java.time.Instant

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

data class RecipeReagent(
    val itemId: Int,
    val name: LocaleDTO,
    val quantity: Int,
)

data class Recipe(
    val id: Int,
    val name: LocaleDTO,
    val description: LocaleDTO? = null,
    val mediaUrl: String? = null,
    val lastModified: Instant? = null,
    val rank: Int? = null,
    val craftedItemId: Int? = null,
    val craftedQuantity: Int? = null,
    val reagents: List<RecipeReagent> = emptyList(),
    val modifiedCraftingSlots: List<ModifiedCraftingSlot> = emptyList(),
)
