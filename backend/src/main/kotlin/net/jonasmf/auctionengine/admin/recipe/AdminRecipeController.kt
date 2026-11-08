package net.jonasmf.auctionengine.admin.recipe

import net.jonasmf.auctionengine.generated.api.AdminRecipeApi
import net.jonasmf.auctionengine.generated.model.AdminRecipe
import net.jonasmf.auctionengine.generated.model.AdminRecipeBulkOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipeCompareResponse
import net.jonasmf.auctionengine.generated.model.AdminRecipeOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipePage
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminRecipeController(
    private val adminRecipeService: AdminRecipeService,
) : AdminRecipeApi {
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun searchAdminRecipes(
        query: String?,
        locale: String?,
        professionId: Int?,
        hasOverride: Boolean?,
        itemClassId: Int?,
        itemSubclassId: Int?,
        expansionId: Int?,
        associatedItemId: Int?,
        associationType: String?,
        page: Int,
        pageSize: Int,
    ): ResponseEntity<AdminRecipePage> =
        ResponseEntity.ok(
            adminRecipeService.searchRecipes(
                query,
                locale,
                professionId,
                hasOverride,
                itemClassId,
                itemSubclassId,
                expansionId,
                associatedItemId,
                associationType,
                page,
                pageSize,
            ),
        )

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminRecipe(
        id: Int,
        locale: String?,
        includeBase: Boolean,
        includeOverride: Boolean,
    ): ResponseEntity<AdminRecipe> =
        ResponseEntity.ok(adminRecipeService.getRecipe(id, locale, includeBase, includeOverride))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun upsertAdminRecipeOverride(
        id: Int,
        body: AdminRecipeOverrideRequest,
    ): ResponseEntity<AdminRecipe> = ResponseEntity.ok(adminRecipeService.upsertOverride(id, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun deleteAdminRecipeOverride(id: Int): ResponseEntity<Unit> {
        adminRecipeService.deleteOverride(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun compareAdminRecipeWithApi(id: Int): ResponseEntity<AdminRecipeCompareResponse> =
        ResponseEntity.ok(adminRecipeService.compareWithApi(id))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun bulkUpsertAdminRecipeOverrides(
        body: AdminRecipeBulkOverrideRequest,
    ): ResponseEntity<List<AdminRecipe>> = ResponseEntity.ok(adminRecipeService.bulkUpsertOverrides(body))
}
