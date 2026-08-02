package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.generated.model.AdminItem1
import net.jonasmf.auctionengine.generated.model.AdminItemCreateRequest
import net.jonasmf.auctionengine.generated.model.AdminItemFields
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipeSearchResult
import net.jonasmf.auctionengine.generated.model.PageMetadata

data class AdminItemSearchResult(
    val items: List<AdminItem1>,
    val totalItems: Long,
)

data class AdminItemRows(
    val effective: AdminItemFields,
    val base: AdminItemFields?,
    val override: AdminItemFields?,
) {
    fun toAdminItem(
        includeBase: Boolean,
        includeOverride: Boolean,
    ): AdminItem1 =
        AdminItem1(
            id = effective.id ?: error("Effective item id is missing"),
            hasBase = base != null,
            hasOverride = override != null,
            effective = effective,
            base = base.takeIf { includeBase },
            `override` = override.takeIf { includeOverride },
        )
}

interface AdminItemRepositoryPort {
    fun searchItems(query: String?, hasBase: Boolean?, hasOverride: Boolean?, itemClassId: Int?, itemSubclassId: Int?, expansionId: Int?, hasRecipe: Boolean?, page: Int, pageSize: Int, localeColumnSuffix: String): AdminItemSearchResult
    fun pageMetadata(page: Int, pageSize: Int, totalItems: Long): PageMetadata
    fun findItemRows(id: Int, localeColumnSuffix: String): AdminItemRows?
    fun hasAnyItemRow(id: Int): Boolean
    fun hasBaseItem(id: Int): Boolean
    fun hasOverrideItem(id: Int): Boolean
    fun qualityId(type: String): Long?
    fun inventoryTypeId(type: String): Long?
    fun bindingId(type: String): Long?
    fun itemClassExists(id: Int): Boolean
    fun itemSubclassInternalId(classId: Int, subclassId: Int): Long?
    fun expansionExists(expansionId: Int): Boolean
    fun upsertOverride(id: Int, request: AdminItemOverrideRequest, itemSubclassInternalId: Long?)
    fun createOverrideOnly(request: AdminItemCreateRequest, itemSubclassInternalId: Long)
    fun deleteOverride(id: Int): Boolean
    fun recipeExists(recipeId: Int): Boolean
    fun searchRecipes(query: String?, limit: Int, localeColumnSuffix: String): List<AdminRecipeSearchResult>
    fun updateRecipeCraftedItem(recipeId: Int, craftedItemId: Int?, craftedQuantity: Int?): Boolean
}
