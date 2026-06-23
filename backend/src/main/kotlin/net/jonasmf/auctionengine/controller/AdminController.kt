package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.AdminApi
import net.jonasmf.auctionengine.generated.model.AdminStatus
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.service.admin.AdminStatusService
import net.jonasmf.auctionengine.service.admin.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminController(
    private val userService: UserService,
    private val adminStatusService: AdminStatusService,
) : AdminApi {
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminStatus(): ResponseEntity<AdminStatus> = ResponseEntity.ok(adminStatusService.getStatus())

    // TODO: Need a paginated response - Update openApi as well
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listUsers(): ResponseEntity<List<User>> = ResponseEntity.ok(userService.getUsers())
}
