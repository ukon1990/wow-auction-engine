package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.AdminItem
import net.jonasmf.auctionengine.generated.model.AdminItemApiCompareField
import net.jonasmf.auctionengine.generated.model.AdminItemApiCompareResponse
import net.jonasmf.auctionengine.generated.model.AdminItemBulkOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemCreateRequest
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemPage
import net.jonasmf.auctionengine.integration.blizzard.ItemApiClient
import net.jonasmf.auctionengine.mapper.hasEnglishName
import net.jonasmf.auctionengine.mapper.toLocaleDTO
import net.jonasmf.auctionengine.repository.rds.AdminExpansionRepository
import net.jonasmf.auctionengine.repository.rds.AdminItemRepository
import net.jonasmf.auctionengine.repository.rds.AdminItemSearchQuery
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AdminItemService(
    private val adminItemRepository: AdminItemRepository,
    private val adminExpansionRepository: AdminExpansionRepository,
    private val itemApiClient: ItemApiClient,
) {
    fun listItems(
        page: Int,
        pageSize: Int,
        itemId: Int?,
        name: String?,
        qualityId: Long?,
        classId: Int?,
        subclassId: Int?,
        expansionId: Int?,
        hasOverride: Boolean?,
        sort: String,
        locale: String?,
    ): AdminItemPage =
        adminItemRepository.search(
            AdminItemSearchQuery(
                page = page,
                pageSize = pageSize,
                itemId = itemId,
                name = name,
                qualityId = qualityId,
                classId = classId,
                subclassId = subclassId,
                expansionId = expansionId,
                hasOverride = hasOverride,
                sort = sort,
                localeColumnSuffix = AdminExpansionRepository.resolveLocaleColumnSuffix(locale),
            ),
        )

    fun getItem(
        id: Int,
        includeBase: Boolean,
        includeOverride: Boolean,
        locale: String?,
    ): AdminItem =
        adminItemRepository.findAdminItem(
            itemId = id,
            includeBase = includeBase,
            includeOverride = includeOverride,
            localeColumnSuffix = AdminExpansionRepository.resolveLocaleColumnSuffix(locale),
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: $id")

    fun upsertOverride(
        id: Int,
        request: AdminItemOverrideRequest,
    ): AdminItem {
        validateOverrideRequest(request, requireName = false)
        if (!adminItemRepository.itemExists(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: $id")
        }
        request.expansionId?.let { validateExpansion(it) }
        return runCatching { adminItemRepository.upsertOverride(id, request) }
            .getOrElse { error ->
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "Invalid override")
            }
    }

    fun deleteOverride(id: Int) {
        if (!adminItemRepository.deleteOverride(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Override not found for item: $id")
        }
    }

    fun createOverrideOnly(request: AdminItemCreateRequest): AdminItem {
        validateOverrideRequest(request.toOverrideRequest(), requireName = true)
        request.expansionId?.let { validateExpansion(it) }
        if (adminItemRepository.itemExists(request.id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Item already exists: ${request.id}")
        }
        return runCatching {
            adminItemRepository.createOverrideOnly(request.id, request.toOverrideRequest())
        }.getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "Invalid item")
        }
    }

    fun bulkUpsertOverrides(request: AdminItemBulkOverrideRequest): List<AdminItem> {
        if (request.overrides.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one override entry is required")
        }
        return request.overrides.map { entry ->
            val overrideRequest = entry.toOverrideRequest()
            validateOverrideRequest(overrideRequest, requireName = false)
            entry.expansionId?.let { validateExpansion(it) }
            if (!adminItemRepository.itemExists(entry.id) && !adminItemRepository.hasOverrideRow(entry.id)) {
                runCatching {
                    adminItemRepository.createOverrideOnly(entry.id, overrideRequest)
                }.getOrElse { error ->
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Item ${entry.id}: ${error.message ?: "Invalid override"}",
                    )
                }
            } else {
                runCatching {
                    adminItemRepository.upsertOverride(entry.id, overrideRequest, requireBase = false)
                }.getOrElse { error ->
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Item ${entry.id}: ${error.message ?: "Invalid override"}",
                    )
                }
            }
        }
    }

    fun compareWithApi(
        id: Int,
        locale: String?,
    ): AdminItemApiCompareResponse {
        if (!adminItemRepository.itemExists(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: $id")
        }
        val localeSuffix = AdminExpansionRepository.resolveLocaleColumnSuffix(locale)
        val effective =
            adminItemRepository.findAdminItem(id, localeColumnSuffix = localeSuffix)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: $id")
        val baseRow = adminItemRepository.findItemRow(id, isOverride = false)
        val overrideRow = adminItemRepository.findItemRow(id, isOverride = true)
        val apiItem = runCatching { itemApiClient.getById(id) }.getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch item from Blizzard API: ${error.message}")
        }

        val fields =
            linkedMapOf(
                "level" to compareField(baseRow?.level, overrideRow?.level, apiItem.level, effective.level),
                "requiredLevel" to
                    compareField(
                        baseRow?.requiredLevel,
                        overrideRow?.requiredLevel,
                        apiItem.requiredLevel,
                        effective.requiredLevel,
                    ),
                "maxCount" to compareField(baseRow?.maxCount, overrideRow?.maxCount, apiItem.maxCount, effective.maxCount),
                "mediaUrl" to compareField(baseRow?.mediaUrl, overrideRow?.mediaUrl, apiItem.mediaUrl, effective.mediaUrl),
                "mediaSourceUrl" to
                    compareField(
                        baseRow?.mediaSourceUrl,
                        overrideRow?.mediaSourceUrl,
                        apiItem.mediaSourceUrl,
                        effective.mediaSourceUrl,
                    ),
                "purchasePrice" to
                    compareField(
                        baseRow?.purchasePrice,
                        overrideRow?.purchasePrice,
                        apiItem.purchasePrice,
                        effective.purchasePrice,
                    ),
                "purchaseQuantity" to
                    compareField(
                        baseRow?.purchaseQuantity,
                        overrideRow?.purchaseQuantity,
                        apiItem.purchaseQuantity,
                        effective.purchaseQuantity,
                    ),
                "sellPrice" to compareField(baseRow?.sellPrice, overrideRow?.sellPrice, apiItem.sellPrice, effective.sellPrice),
                "isEquippable" to
                    compareField(
                        baseRow?.isEquippable,
                        overrideRow?.isEquippable,
                        apiItem.isEquippable,
                        effective.isEquippable,
                    ),
                "isStackable" to
                    compareField(
                        baseRow?.isStackable,
                        overrideRow?.isStackable,
                        apiItem.isStackable,
                        effective.isStackable,
                    ),
                "expansionId" to compareField(baseRow?.expansionId, overrideRow?.expansionId, null, effective.expansionId),
            )
        return AdminItemApiCompareResponse(fields = fields)
    }

    private fun validateOverrideRequest(
        request: AdminItemOverrideRequest,
        requireName: Boolean,
    ) {
        if (requireName) {
            val locales = request.nameLocales?.toLocaleDTO()
            if (locales == null || !locales.hasEnglishName()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one English name (en_US or en_GB) is required")
            }
        } else if (request.nameLocales != null && !request.nameLocales.toLocaleDTO().hasEnglishName()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "When provided, nameLocales must include en_US or en_GB")
        }
    }

    private fun validateExpansion(expansionId: Int) {
        if (!adminExpansionRepository.expansionExists(expansionId)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Expansion not found: $expansionId")
        }
    }

    private fun compareField(
        base: Any?,
        override: Any?,
        api: Any?,
        effective: Any?,
    ): AdminItemApiCompareField =
        AdminItemApiCompareField(
            base = base,
            `override` = override,
            api = api,
            effective = effective,
        )
}

private fun AdminItemCreateRequest.toOverrideRequest(): AdminItemOverrideRequest =
    AdminItemOverrideRequest(
        nameLocales = nameLocales,
        qualityId = qualityId,
        itemClassId = itemClassId,
        itemSubclassId = itemSubclassId,
        expansionId = expansionId,
        level = level,
        requiredLevel = requiredLevel,
        maxCount = maxCount,
        mediaUrl = mediaUrl,
        mediaSourceUrl = mediaSourceUrl,
        purchasePrice = purchasePrice,
        purchaseQuantity = purchaseQuantity,
        sellPrice = sellPrice,
        bindingId = bindingId,
        inventoryTypeId = inventoryTypeId,
        isEquippable = isEquippable,
        isStackable = isStackable,
        overrideNote = overrideNote,
    )

private fun net.jonasmf.auctionengine.generated.model.AdminItemBulkOverrideEntry.toOverrideRequest(): AdminItemOverrideRequest =
    AdminItemOverrideRequest(
        nameLocales = nameLocales,
        qualityId = qualityId,
        itemClassId = itemClassId,
        itemSubclassId = itemSubclassId,
        expansionId = expansionId,
        level = level,
        requiredLevel = requiredLevel,
        maxCount = maxCount,
        mediaUrl = mediaUrl,
        mediaSourceUrl = mediaSourceUrl,
        purchasePrice = purchasePrice,
        purchaseQuantity = purchaseQuantity,
        sellPrice = sellPrice,
        bindingId = bindingId,
        inventoryTypeId = inventoryTypeId,
        isEquippable = isEquippable,
        isStackable = isStackable,
        overrideNote = overrideNote,
    )
