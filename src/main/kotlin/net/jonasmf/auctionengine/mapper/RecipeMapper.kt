package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
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
import java.time.Instant

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

fun RecipeDTO.toDomain(lastModified: Instant? = null) =
    Recipe(
        id = id,
        name = name,
        description = description,
        mediaUrl = media.key.href,
        lastModified = lastModified,
        rank = rank,
        craftedItemId = craftedItem?.id,
        craftedQuantity = craftedQuantity?.value,
        reagents = reagents.map { it.toDomain() },
        modifiedCraftingSlots = modifiedCraftingSlots.map { it.toDomain() },
    )

fun RecipeReagent.toDBO(
    recipeId: Int,
    reagentIndex: Int,
) = RecipeReagentDBO(
    itemId = itemId,
    name =
        name.toDBO(
            LocaleSourceType.RECIPE_REAGENT,
            localeSourceKey(recipeId, reagentIndex, itemId),
            "name",
        ),
    quantity = quantity,
)

fun RecipeReagentDBO.toDomain() =
    RecipeReagent(
        itemId = itemId,
        name = name.toDTO(),
        quantity = quantity,
    )

fun ModifiedCraftingSlot.toDBO(
    recipeId: Int,
    slotIndex: Int,
) = ModifiedCraftingSlotDBO(
    slotTypeId = id,
    description =
        description.toDBO(
            LocaleSourceType.MODIFIED_CRAFTING_SLOT,
            localeSourceKey(recipeId, slotIndex, id),
            "description",
        ),
    compatibleCategories = compatibleCategories.map { it.toDBO(recipeId, slotIndex, id) }.toMutableList(),
    displayOrder = displayOrder,
)

fun ModifiedCraftingSlotDBO.toDomain() =
    ModifiedCraftingSlot(
        id = slotTypeId,
        description = description.toDTO(),
        compatibleCategories = compatibleCategories.map { it.toDomain() },
        displayOrder = displayOrder,
    )

fun Recipe.toDBO() =
    RecipeDBO(
        id = id,
        name = name.toDBO(LocaleSourceType.RECIPE, localeSourceKey(id), "name"),
        description = description?.toDBO(LocaleSourceType.RECIPE, localeSourceKey(id), "description"),
        mediaUrl = mediaUrl,
        lastModified = lastModified,
        rank = rank,
        craftedItemId = craftedItemId,
        craftedQuantity = craftedQuantity,
        reagents = reagents.mapIndexed { index, reagent -> reagent.toDBO(id, index) }.toMutableList(),
        modifiedCraftingSlots =
            modifiedCraftingSlots
                .mapIndexed {
                    index,
                    slot,
                    ->
                    slot.toDBO(id, index)
                }.toMutableList(),
    )

fun RecipeDBO.toDomain() =
    Recipe(
        id = id,
        name = name.toDTO(),
        description = description?.toDTO(),
        mediaUrl = mediaUrl,
        lastModified = lastModified,
        rank = rank,
        craftedItemId = craftedItemId,
        craftedQuantity = craftedQuantity,
        reagents = reagents.map { it.toDomain() },
        modifiedCraftingSlots = modifiedCraftingSlots.map { it.toDomain() },
    )
