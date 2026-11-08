package net.jonasmf.auctionengine.admin

import net.jonasmf.auctionengine.generated.api.AdminApi
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.generated.model.AdminSqlExecuteRequest
import net.jonasmf.auctionengine.generated.model.AdminSqlMetadata
import net.jonasmf.auctionengine.generated.model.AdminSqlResult
import net.jonasmf.auctionengine.generated.model.AdminStatus
import net.jonasmf.auctionengine.generated.model.User
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminController(
    private val userService: UserService,
    private val adminStatusService: AdminStatusService,
    private val adminSqlService: AdminSqlService,
    private val adminJobService: AdminJobService,
) : AdminApi {
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminStatus(): ResponseEntity<AdminStatus> =
        ResponseEntity.ok(adminStatusService.getStatus())

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun executeAdminSql(body: AdminSqlExecuteRequest): ResponseEntity<AdminSqlResult> =
        ResponseEntity.ok(adminSqlService.execute(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminSqlMetadata(): ResponseEntity<AdminSqlMetadata> =
        ResponseEntity.ok(adminSqlService.getMetadata())

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminJob(id: Long): ResponseEntity<AdminJob> = ResponseEntity.ok(adminJobService.getJob(id))

    // TODO: Need a paginated response - Update openApi as well
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listUsers(): ResponseEntity<List<User>> = ResponseEntity.ok(userService.getUsers())
}

/** The signed-in admin's name, recorded on the background jobs they start. */
internal fun requestedBy(): String? = SecurityContextHolder.getContext().authentication?.name
