package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.AdminApi
import net.jonasmf.auctionengine.generated.model.AdminExpansion1
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRange
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.generated.model.AdminExpansionRequest
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.generated.model.AdminSqlExecuteRequest
import net.jonasmf.auctionengine.generated.model.AdminSqlMetadata
import net.jonasmf.auctionengine.generated.model.AdminSqlResult
import net.jonasmf.auctionengine.generated.model.AdminStatus
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.service.admin.AdminExpansionService
import net.jonasmf.auctionengine.service.admin.AdminJobService
import net.jonasmf.auctionengine.service.admin.AdminSqlService
import net.jonasmf.auctionengine.service.admin.AdminStatusService
import net.jonasmf.auctionengine.service.admin.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class AdminController(
    private val userService: UserService,
    private val adminStatusService: AdminStatusService,
    private val adminSqlService: AdminSqlService,
    private val adminExpansionService: AdminExpansionService,
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

    // TODO: Need a paginated response - Update openApi as well
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listUsers(): ResponseEntity<List<User>> = ResponseEntity.ok(userService.getUsers())

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(error: ResponseStatusException): ResponseEntity<Map<String, Any>> =
        ResponseEntity
            .status(error.statusCode)
            .body(
                mapOf(
                    "status" to error.statusCode.value(),
                    "detail" to (error.reason ?: error.statusCode.toString()),
                ),
            )

    private fun requestedBy(): String? = SecurityContextHolder.getContext().authentication?.name
}
