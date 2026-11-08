package net.jonasmf.auctionengine.admin.item

import net.jonasmf.auctionengine.generated.api.AdminItemApi
import net.jonasmf.auctionengine.generated.model.AdminItem
import net.jonasmf.auctionengine.generated.model.AdminItemBulkOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemCompareResponse
import net.jonasmf.auctionengine.generated.model.AdminItemCreateRequest
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemPage
import net.jonasmf.auctionengine.generated.model.AdminRecipeAssociationRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminItemController(
    private val adminItemService: AdminItemService,
) : AdminItemApi {
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun searchAdminItems(
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
    ): ResponseEntity<AdminItemPage> =
        ResponseEntity.ok(
            adminItemService.searchItems(
                query,
                locale,
                hasBase,
                hasOverride,
                itemClassId,
                itemSubclassId,
                expansionId,
                hasRecipe,
                page,
                pageSize,
            ),
        )

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminItem(
        id: Int,
        locale: String?,
        includeBase: Boolean,
        includeOverride: Boolean,
    ): ResponseEntity<AdminItem> = ResponseEntity.ok(adminItemService.getItem(id, locale, includeBase, includeOverride))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun upsertAdminItemOverride(
        id: Int,
        body: AdminItemOverrideRequest,
    ): ResponseEntity<AdminItem> = ResponseEntity.ok(adminItemService.upsertOverride(id, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun upsertAdminItemRecipeAssociation(
        id: Int,
        recipeId: Int,
        body: AdminRecipeAssociationRequest,
    ): ResponseEntity<AdminItem> = ResponseEntity.ok(adminItemService.upsertRecipeAssociation(id, recipeId, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun deleteAdminItemOverride(id: Int): ResponseEntity<Unit> {
        adminItemService.deleteOverride(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun createAdminItem(body: AdminItemCreateRequest): ResponseEntity<AdminItem> =
        ResponseEntity.ok(adminItemService.createOverrideOnly(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun compareAdminItemWithApi(id: Int): ResponseEntity<AdminItemCompareResponse> =
        ResponseEntity.ok(adminItemService.compareWithApi(id))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun bulkUpsertAdminItemOverrides(
        body: AdminItemBulkOverrideRequest,
    ): ResponseEntity<List<AdminItem>> = ResponseEntity.ok(adminItemService.bulkUpsertOverrides(body))
}
