package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.rds.profession.ModifiedCraftingSlotDBO
import net.jonasmf.auctionengine.dbo.rds.profession.RecipeDBO
import net.jonasmf.auctionengine.dbo.rds.profession.RecipeReagentDBO
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingSlot
import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.domain.profession.RecipeReagent
import net.jonasmf.auctionengine.dto.ReferenceDTO
import net.jonasmf.auctionengine.dto.recipe.RecipeDTO
import net.jonasmf.auctionengine.dto.recipe.RecipeModifiedCraftingSlotDTO
import net.jonasmf.auctionengine.dto.recipe.RecipeReagentDTO

fun ReferenceDTO.toRecipeStub() =
    Recipe(
        id = id,
        name = name,
    )

fun RecipeReagentDTO.toDomain() =
    RecipeReagent(
        itemId = reagent.id,
        name = reagent.name,
        quantity = quantity,
    )

fun RecipeModifiedCraftingSlotDTO.toDomain() =
    ModifiedCraftingSlot(
        id = slotType.id,
        description = slotType.name,
        compatibleCategories = emptyList(),
        displayOrder = displayOrder,
    )

fun RecipeDTO.toDomain() =
    Recipe(
        id = id,
        name = name,
        description = description,
        mediaUrl = media.key.href,
        rank = rank,
        craftedItemId = craftedItem?.id,
        craftedQuantity = craftedQuantity?.value,
        reagents = reagents.map { it.toDomain() },
        modifiedCraftingSlots = modifiedCraftingSlots.map { it.toDomain() },
    )

fun RecipeReagent.toDBO() =
    RecipeReagentDBO(
        itemId = itemId,
        name = name.toDBO(),
        quantity = quantity,
    )

fun RecipeReagentDBO.toDomain() =
    RecipeReagent(
        itemId = itemId,
        name = name.toDTO(),
        quantity = quantity,
    )

fun ModifiedCraftingSlot.toDBO() =
    ModifiedCraftingSlotDBO(
        id = id,
        description = description.toDBO(),
        compatibleCategories = compatibleCategories.map { it.toDBO() }.toMutableList(),
        displayOrder = displayOrder,
    )

fun ModifiedCraftingSlotDBO.toDomain() =
    ModifiedCraftingSlot(
        id = id,
        description = description.toDTO(),
        compatibleCategories = compatibleCategories.map { it.toDomain() },
        displayOrder = displayOrder,
    )

fun Recipe.toDBO() =
    RecipeDBO(
        id = id,
        name = name.toDBO(),
        description = description?.toDBO(),
        mediaUrl = mediaUrl,
        rank = rank,
        craftedItemId = craftedItemId,
        craftedQuantity = craftedQuantity,
        reagents = reagents.map { it.toDBO() }.toMutableList(),
        modifiedCraftingSlots = modifiedCraftingSlots.map { it.toDBO() }.toMutableList(),
    )

fun RecipeDBO.toDomain() =
    Recipe(
        id = id,
        name = name.toDTO(),
        description = description?.toDTO(),
        mediaUrl = mediaUrl,
        rank = rank,
        craftedItemId = craftedItemId,
        craftedQuantity = craftedQuantity,
        reagents = reagents.map { it.toDomain() },
        modifiedCraftingSlots = modifiedCraftingSlots.map { it.toDomain() },
    )
