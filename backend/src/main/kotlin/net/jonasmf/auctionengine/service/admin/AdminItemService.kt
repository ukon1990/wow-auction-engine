package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.generated.model.AdminItem1
import net.jonasmf.auctionengine.generated.model.AdminItemBulkOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemCompareField
import net.jonasmf.auctionengine.generated.model.AdminItemCompareResponse
import net.jonasmf.auctionengine.generated.model.AdminItemCreateRequest
import net.jonasmf.auctionengine.generated.model.AdminItemFields
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemPage
import net.jonasmf.auctionengine.generated.model.AdminRecipeAssociationRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipeSearchResult
import net.jonasmf.auctionengine.integration.blizzard.ItemApiLookup
import net.jonasmf.auctionengine.mapper.hasEnglishName
import net.jonasmf.auctionengine.mapper.toGameLocale
import net.jonasmf.auctionengine.mapper.toLocaleDTO
import net.jonasmf.auctionengine.repository.rds.AdminExpansionRepository
import net.jonasmf.auctionengine.repository.rds.AdminItemRepositoryPort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val MAX_ADMIN_ITEM_PAGE_SIZE = 100
private const val MAX_ADMIN_RECIPE_SEARCH_LIMIT = 50

@Service
class AdminItemService(
    private val adminItemRepository: AdminItemRepositoryPort,
    private val itemApiClient: ItemApiLookup,
) {
    fun searchItems(
        query: String?,
        locale: String?,
        hasBase: Boolean?,
        hasOverride: Boolean?,
        itemClassId: Int?,
        itemSubclassId: Int?,
        expansionId: Int?,
        hasRecipe: Boolean?,
        page: Int,
        pageSize: Int,
    ): AdminItemPage {
        validatePagination(page, pageSize)
        if (itemSubclassId != null && itemClassId == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "itemClassId is required when itemSubclassId is set")
        }
        expansionId?.let { requireExpansion(it) }
        val result =
            adminItemRepository.searchItems(
                query = query,
                hasBase = hasBase,
                hasOverride = hasOverride,
                itemClassId = itemClassId,
                itemSubclassId = itemSubclassId,
                expansionId = expansionId,
                hasRecipe = hasRecipe,
                page = page,
                pageSize = pageSize,
                localeColumnSuffix = AdminExpansionRepository.resolveLocaleColumnSuffix(locale),
            )
        return AdminItemPage(
            items = result.items,
            page = adminItemRepository.pageMetadata(page, pageSize, result.totalItems),
        )
    }

    fun getItem(
        id: Int,
        locale: String?,
        includeBase: Boolean,
        includeOverride: Boolean,
    ): AdminItem1 =
        findItem(id, locale)
            .toAdminItem(includeBase = includeBase, includeOverride = includeOverride)

    fun searchRecipes(
        query: String?,
        locale: String?,
        limit: Int,
    ): List<AdminRecipeSearchResult> {
        if (limit !in 1..MAX_ADMIN_RECIPE_SEARCH_LIMIT) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50")
        }
        return adminItemRepository.searchRecipes(
            query = query,
            limit = limit,
            localeColumnSuffix = AdminExpansionRepository.resolveLocaleColumnSuffix(locale),
        )
    }

    @Transactional
    fun upsertOverride(
        id: Int,
        request: AdminItemOverrideRequest,
    ): AdminItem1 {
        if (!adminItemRepository.hasAnyItemRow(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: $id")
        }
        validateOverrideRequest(id, request)
        if (!adminItemRepository.hasBaseItem(id)) {
            validateOverrideOnlyRequest(request)
        }
        val subclassInternalId = resolveOverrideSubclassId(request)
        adminItemRepository.upsertOverride(id, request, subclassInternalId)
        return getItem(id, locale = null, includeBase = true, includeOverride = true)
    }

    @Transactional
    fun upsertRecipeAssociation(
        id: Int,
        recipeId: Int,
        request: AdminRecipeAssociationRequest,
    ): AdminItem1 {
        if (!adminItemRepository.hasAnyItemRow(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: $id")
        }
        if (!adminItemRepository.recipeExists(recipeId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found: $recipeId")
        }

        val craftedItemId = request.craftedItemId
        if (craftedItemId != null && craftedItemId != id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "craftedItemId must match path item id")
        }
        if (craftedItemId == null) {
            adminItemRepository.updateRecipeCraftedItem(recipeId, craftedItemId = null, craftedQuantity = null)
        } else {
            val craftedQuantity =
                request.craftedQuantity
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "craftedQuantity is required")
            if (craftedQuantity < 1) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "craftedQuantity must be at least 1")
            }
            adminItemRepository.updateRecipeCraftedItem(recipeId, craftedItemId = id, craftedQuantity = craftedQuantity)
        }

        return getItem(id, locale = null, includeBase = true, includeOverride = true)
    }

    @Transactional
    fun bulkUpsertOverrides(request: AdminItemBulkOverrideRequest): List<AdminItem1> {
        if (request.overrides.size > MAX_ADMIN_ITEM_PAGE_SIZE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Bulk override request cannot exceed 100 items")
        }
        val seenIds = mutableSetOf<Int>()
        request.overrides.forEach { override ->
            if (!seenIds.add(override.id)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate item override id: ${override.id}")
            }
            if (!adminItemRepository.hasBaseItem(override.id)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Base item not found: ${override.id}")
            }
            validateOverrideRequest(override.id, override.`override`)
        }
        request.overrides.forEach { override ->
            adminItemRepository.upsertOverride(
                override.id,
                override.`override`,
                resolveOverrideSubclassId(override.`override`),
            )
        }
        return request.overrides.map { getItem(it.id, locale = null, includeBase = true, includeOverride = true) }
    }

    @Transactional
    fun deleteOverride(id: Int) {
        if (!adminItemRepository.deleteOverride(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Override item not found: $id")
        }
    }

    @Transactional
    fun createOverrideOnly(request: AdminItemCreateRequest): AdminItem1 {
        validateCreateRequest(request)
        if (adminItemRepository.hasAnyItemRow(request.id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Item already exists: ${request.id}")
        }
        val subclassInternalId = requireItemSubclass(request.itemClassId, request.itemSubclassId)
        adminItemRepository.createOverrideOnly(request, subclassInternalId)
        return getItem(request.id, locale = null, includeBase = true, includeOverride = true)
    }

    fun compareWithApi(id: Int): AdminItemCompareResponse {
        val local = findItem(id, locale = null)
        val apiItem =
            runCatching { itemApiClient.getById(id) }
                .getOrElse { error ->
                    throw ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Blizzard API item not found or unavailable: $id",
                        error,
                    )
                }
        val baseValues = local.base?.toCompareValues().orEmpty()
        val overrideValues = local.override?.toCompareValues().orEmpty()
        val effectiveValues = local.effective.toCompareValues()
        val apiValues = apiItem.toCompareValues()
        val fieldNames = baseValues.keys + overrideValues.keys + effectiveValues.keys + apiValues.keys
        return AdminItemCompareResponse(
            itemId = id,
            fields =
                fieldNames.associateWith { field ->
                    AdminItemCompareField(
                        base = baseValues[field],
                        `override` = overrideValues[field],
                        api = apiValues[field],
                        effective = effectiveValues[field],
                    )
                },
        )
    }

    private fun findItem(
        id: Int,
        locale: String?,
    ) = adminItemRepository.findItemRows(
        id = id,
        localeColumnSuffix = AdminExpansionRepository.resolveLocaleColumnSuffix(locale),
    ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: $id")

    private fun validatePagination(
        page: Int,
        pageSize: Int,
    ) {
        if (page < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be at least 1")
        }
        if (pageSize !in 1..MAX_ADMIN_ITEM_PAGE_SIZE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "pageSize must be between 1 and 100")
        }
    }

    private fun validateOverrideRequest(
        id: Int,
        request: AdminItemOverrideRequest,
    ) {
        request.nameLocales?.let { nameLocales ->
            if (!nameLocales.toLocaleDTO().hasEnglishName()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one English item name is required")
            }
        }
        request.qualityType?.let { requireQuality(it) }
        request.inventoryType?.let { requireInventoryType(it) }
        request.bindingType?.let { requireBindingType(it) }
        validateOptionalNonNegative("level", request.level)
        validateOptionalNonNegative("requiredLevel", request.requiredLevel)
        validateOptionalNonNegative("purchasePrice", request.purchasePrice)
        validateOptionalNonNegative("sellPrice", request.sellPrice)
        validateOptionalNonNegative("maxCount", request.maxCount)
        validateOptionalNonNegative("purchaseQuantity", request.purchaseQuantity)
        request.itemClassId?.let { requireItemClass(it) }
        if ((request.itemClassId == null) != (request.itemSubclassId == null)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "itemClassId and itemSubclassId must be provided together",
            )
        }
        if (request.itemClassId != null && request.itemSubclassId != null) {
            requireItemSubclass(request.itemClassId, request.itemSubclassId)
        }
        request.expansionId?.let { requireExpansion(it) }
        if (request.mediaUrl?.isBlank() == true) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "mediaUrl cannot be blank")
        }
    }

    private fun validateOverrideOnlyRequest(request: AdminItemOverrideRequest) {
        val nameLocales =
            request.nameLocales
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Override-only items require localized names",
                )
        if (!nameLocales.toLocaleDTO().hasEnglishName()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one English item name is required")
        }
        requireQuality(request.qualityType ?: missingOverrideOnlyField("qualityType"))
        validateRequiredNonNegative("level", request.level)
        validateRequiredNonNegative("requiredLevel", request.requiredLevel)
        val mediaUrl = request.mediaUrl ?: missingOverrideOnlyField("mediaUrl")
        if (mediaUrl.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "mediaUrl is required")
        }
        val classId = request.itemClassId ?: missingOverrideOnlyField("itemClassId")
        val subclassId = request.itemSubclassId ?: missingOverrideOnlyField("itemSubclassId")
        requireItemClass(classId)
        requireItemSubclass(classId, subclassId)
        requireInventoryType(request.inventoryType ?: missingOverrideOnlyField("inventoryType"))
        request.bindingType?.let { requireBindingType(it) }
        validateRequiredNonNegative("purchasePrice", request.purchasePrice)
        validateRequiredNonNegative("sellPrice", request.sellPrice)
        validateRequiredNonNegative("maxCount", request.maxCount)
        if (request.isEquippable == null) {
            missingOverrideOnlyField("isEquippable")
        }
        if (request.isStackable == null) {
            missingOverrideOnlyField("isStackable")
        }
        validateRequiredNonNegative("purchaseQuantity", request.purchaseQuantity)
        request.expansionId?.let { requireExpansion(it) }
    }

    private fun validateCreateRequest(request: AdminItemCreateRequest) {
        if (!request.nameLocales.toLocaleDTO().hasEnglishName()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one English item name is required")
        }
        if (request.mediaUrl.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "mediaUrl is required")
        }
        validateNonNegative("level", request.level)
        validateNonNegative("requiredLevel", request.requiredLevel)
        validateNonNegative("purchasePrice", request.purchasePrice)
        validateNonNegative("sellPrice", request.sellPrice)
        validateNonNegative("maxCount", request.maxCount)
        validateNonNegative("purchaseQuantity", request.purchaseQuantity)
        requireQuality(request.qualityType)
        requireInventoryType(request.inventoryType)
        request.bindingType?.let { requireBindingType(it) }
        requireItemClass(request.itemClassId)
        requireItemSubclass(request.itemClassId, request.itemSubclassId)
        request.expansionId?.let { requireExpansion(it) }
    }

    private fun resolveOverrideSubclassId(request: AdminItemOverrideRequest): Long? {
        if (request.itemClassId == null || request.itemSubclassId == null) return null
        return requireItemSubclass(request.itemClassId, request.itemSubclassId)
    }

    private fun requireQuality(type: String): Long {
        if (type.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "qualityType is required")
        }
        return adminItemRepository.qualityId(type)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Item quality not found: $type")
    }

    private fun requireInventoryType(type: String): Long {
        if (type.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "inventoryType is required")
        }
        return adminItemRepository.inventoryTypeId(type)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Inventory type not found: $type")
    }

    private fun requireBindingType(type: String): Long {
        if (type.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "bindingType cannot be blank")
        }
        return adminItemRepository.bindingId(type)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Item binding not found: $type")
    }

    private fun requireItemClass(id: Int) {
        if (!adminItemRepository.itemClassExists(id)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Item class not found: $id")
        }
    }

    private fun requireItemSubclass(
        classId: Int,
        subclassId: Int,
    ): Long =
        adminItemRepository.itemSubclassInternalId(classId, subclassId)
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Item subclass not found: classId=$classId subclassId=$subclassId",
            )

    private fun requireExpansion(id: Int) {
        if (!adminItemRepository.expansionExists(id)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Expansion not found: $id")
        }
    }

    private fun validateOptionalNonNegative(
        field: String,
        value: Int?,
    ) {
        value?.let { validateNonNegative(field, it) }
    }

    private fun validateNonNegative(
        field: String,
        value: Int,
    ) {
        if (value < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must be non-negative")
        }
    }

    private fun validateRequiredNonNegative(
        field: String,
        value: Int?,
    ) {
        validateNonNegative(field, value ?: missingOverrideOnlyField(field))
    }

    private fun missingOverrideOnlyField(field: String): Nothing {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Override-only items require $field")
    }
}

private fun AdminItemFields.toCompareValues(): Map<String, Any?> =
    mapOf(
        "nameLocales" to nameLocales,
        "qualityType" to quality?.type,
        "level" to level,
        "requiredLevel" to requiredLevel,
        "mediaUrl" to mediaUrl,
        "mediaSourceUrl" to mediaSourceUrl,
        "itemClassId" to itemClass?.id?.toInt(),
        "itemSubclassId" to itemSubclass?.id?.toInt(),
        "inventoryType" to inventoryType?.type,
        "bindingType" to binding?.type,
        "purchasePrice" to purchasePrice,
        "sellPrice" to sellPrice,
        "maxCount" to maxCount,
        "isEquippable" to isEquippable,
        "isStackable" to isStackable,
        "purchaseQuantity" to purchaseQuantity,
        "expansionId" to expansion?.id,
        "overrideNote" to overrideNote,
    )

private fun Item.toCompareValues(): Map<String, Any?> =
    mapOf(
        "nameLocales" to name.toGameLocale(),
        "qualityType" to quality.type,
        "level" to level,
        "requiredLevel" to requiredLevel,
        "mediaUrl" to mediaUrl,
        "mediaSourceUrl" to mediaSourceUrl,
        "itemClassId" to itemClass.id,
        "itemSubclassId" to itemSubclass.subclassId,
        "inventoryType" to inventoryType.type,
        "bindingType" to binding?.type,
        "purchasePrice" to purchasePrice,
        "sellPrice" to sellPrice,
        "maxCount" to maxCount,
        "isEquippable" to isEquippable,
        "isStackable" to isStackable,
        "purchaseQuantity" to purchaseQuantity,
    )
