package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.AdminApi
import net.jonasmf.auctionengine.generated.model.AdminExpansion1
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRange
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.generated.model.AdminExpansionRequest
import net.jonasmf.auctionengine.generated.model.AdminItem
import net.jonasmf.auctionengine.generated.model.AdminItemApiCompareResponse
import net.jonasmf.auctionengine.generated.model.AdminItemBulkOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemCreateRequest
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemPage
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.generated.model.AdminSqlExecuteRequest
import net.jonasmf.auctionengine.generated.model.AdminSqlMetadata
import net.jonasmf.auctionengine.generated.model.AdminSqlResult
import net.jonasmf.auctionengine.generated.model.AdminStatus
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.service.admin.AdminExpansionService
import net.jonasmf.auctionengine.service.admin.AdminItemService
import net.jonasmf.auctionengine.service.admin.AdminJobService
import net.jonasmf.auctionengine.service.admin.AdminSqlService
import net.jonasmf.auctionengine.service.admin.AdminStatusService
import net.jonasmf.auctionengine.service.admin.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminController(
    private val userService: UserService,
    private val adminStatusService: AdminStatusService,
    private val adminSqlService: AdminSqlService,
    private val adminExpansionService: AdminExpansionService,
    private val adminItemService: AdminItemService,
    private val adminJobService: AdminJobService,
) : AdminApi {
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminStatus(): ResponseEntity<AdminStatus> = ResponseEntity.ok(adminStatusService.getStatus())

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun executeAdminSql(body: AdminSqlExecuteRequest): ResponseEntity<AdminSqlResult> =
        ResponseEntity.ok(adminSqlService.execute(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminSqlMetadata(): ResponseEntity<AdminSqlMetadata> =
        ResponseEntity.ok(adminSqlService.getMetadata())

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listExpansions(locale: String?): ResponseEntity<List<AdminExpansion1>> =
        ResponseEntity.ok(adminExpansionService.listExpansions(locale))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun createExpansion(body: AdminExpansionRequest): ResponseEntity<AdminExpansion1> =
        ResponseEntity.ok(adminExpansionService.createExpansion(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun updateExpansion(
        id: Int,
        body: AdminExpansionRequest,
    ): ResponseEntity<AdminExpansion1> = ResponseEntity.ok(adminExpansionService.updateExpansion(id, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun deleteExpansion(id: Int): ResponseEntity<Unit> {
        adminExpansionService.deleteExpansion(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listExpansionRanges(locale: String?): ResponseEntity<List<AdminExpansionItemRange>> =
        ResponseEntity.ok(adminExpansionService.listRanges(locale))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun createExpansionRange(body: AdminExpansionItemRangeRequest): ResponseEntity<AdminExpansionItemRange> =
        ResponseEntity.ok(adminExpansionService.createRange(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun updateExpansionRange(
        id: Long,
        body: AdminExpansionItemRangeRequest,
    ): ResponseEntity<AdminExpansionItemRange> = ResponseEntity.ok(adminExpansionService.updateRange(id, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun deleteExpansionRange(id: Long): ResponseEntity<Unit> {
        adminExpansionService.deleteRange(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun applyExpansionRanges(): ResponseEntity<AdminJob> =
        ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(adminExpansionService.applyExpansionRanges(requestedBy()))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun fetchMissingExpansionRangeItems(): ResponseEntity<AdminJob> =
        ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(adminExpansionService.fetchMissingExpansionRangeItems(requestedBy()))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminJob(id: Long): ResponseEntity<AdminJob> =
        ResponseEntity.ok(adminJobService.getJob(id))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listAdminItems(
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
    ): ResponseEntity<AdminItemPage> =
        ResponseEntity.ok(
            adminItemService.listItems(
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
                locale = locale,
            ),
        )

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun createAdminItem(body: AdminItemCreateRequest): ResponseEntity<AdminItem> =
        ResponseEntity.ok(adminItemService.createOverrideOnly(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun bulkAdminItemOverrides(body: AdminItemBulkOverrideRequest): ResponseEntity<List<AdminItem>> =
        ResponseEntity.ok(adminItemService.bulkUpsertOverrides(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminItem(
        id: Int,
        includeBase: Boolean,
        includeOverride: Boolean,
        locale: String?,
    ): ResponseEntity<AdminItem> = ResponseEntity.ok(adminItemService.getItem(id, includeBase, includeOverride, locale))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun upsertAdminItemOverride(
        id: Int,
        body: AdminItemOverrideRequest,
    ): ResponseEntity<AdminItem> = ResponseEntity.ok(adminItemService.upsertOverride(id, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun deleteAdminItemOverride(id: Int): ResponseEntity<Unit> {
        adminItemService.deleteOverride(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun compareAdminItemApi(
        id: Int,
        locale: String?,
    ): ResponseEntity<AdminItemApiCompareResponse> = ResponseEntity.ok(adminItemService.compareWithApi(id, locale))

    // TODO: Need a paginated response - Update openApi as well
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listUsers(): ResponseEntity<List<User>> = ResponseEntity.ok(userService.getUsers())

    private fun requestedBy(): String? = SecurityContextHolder.getContext().authentication?.name
}
