package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.dbo.rds.profession.ModifiedCraftingCategoryDBO
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingCategory
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingSlot
import net.jonasmf.auctionengine.dto.modifiedcrafting.ModifiedCraftingCategoryDTO
import net.jonasmf.auctionengine.dto.modifiedcrafting.ReagentSlotTypeDTO

fun ModifiedCraftingCategoryDTO.toDomain() =
    ModifiedCraftingCategory(
        id = id,
        name = name,
    )

fun ReagentSlotTypeDTO.toDomain() =
    ModifiedCraftingSlot(
        id = id,
        description = description,
        compatibleCategories = compatibleCategories.map { ModifiedCraftingCategory(it.id, it.name) },
    )

fun ModifiedCraftingCategory.toDBO() =
    ModifiedCraftingCategoryDBO(
        categoryId = id,
        name = name.toDBO(LocaleSourceType.MODIFIED_CRAFTING_CATEGORY_METADATA, localeSourceKey(id), "name"),
    )

fun ModifiedCraftingCategory.toDBO(
    recipeId: Int,
    slotId: Int,
) =
    ModifiedCraftingCategoryDBO(
        categoryId = id,
        name =
            name.toDBO(
                LocaleSourceType.MODIFIED_CRAFTING_CATEGORY,
                localeSourceKey(recipeId, slotId, id),
                "name",
            ),
    )

fun ModifiedCraftingCategoryDBO.toDomain() =
    ModifiedCraftingCategory(
        id = categoryId,
        name = name.toDTO(),
    )
