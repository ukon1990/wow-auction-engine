package net.jonasmf.auctionengine.profession

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.locale.toDBO
import net.jonasmf.auctionengine.locale.toDTO
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.dbo.rds.profession.ModifiedCraftingCategoryDBO
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingCategory
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingSlot
import net.jonasmf.auctionengine.locale.orEmpty
import net.jonasmf.auctionengine.profession.dto.ModifiedCraftingCategoryDTO
import net.jonasmf.auctionengine.profession.dto.ReagentSlotTypeDTO

fun ModifiedCraftingCategoryDTO.toDomain() =
    ModifiedCraftingCategory(
        id = id,
        name = name.orEmpty(),
    )

fun ReagentSlotTypeDTO.toDomain() =
    ModifiedCraftingSlot(
        id = id,
        description = description.orEmpty(),
        compatibleCategories = compatibleCategories.map { ModifiedCraftingCategory(it.id, it.resolvedName()) },
    )

fun ModifiedCraftingCategory.toDBO() =
    ModifiedCraftingCategoryDBO(
        categoryId = id,
        name = name.toDBO(LocaleSourceType.MODIFIED_CRAFTING_CATEGORY_METADATA, localeSourceKey(id), "name"),
    )

fun ModifiedCraftingCategory.toDBO(
    recipeId: Int,
    slotIndex: Int,
    slotId: Int,
) = ModifiedCraftingCategoryDBO(
    categoryId = id,
    name =
        name.toDBO(
            LocaleSourceType.MODIFIED_CRAFTING_CATEGORY,
            localeSourceKey(recipeId, slotIndex, slotId, id),
            "name",
        ),
)

fun ModifiedCraftingCategoryDBO.toDomain() =
    ModifiedCraftingCategory(
        id = categoryId,
        name = name.toDTO(),
    )
