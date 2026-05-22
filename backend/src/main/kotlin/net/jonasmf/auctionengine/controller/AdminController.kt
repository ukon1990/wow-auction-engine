package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.AdminApi
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.service.admin.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminController(
    val userService: UserService,
) : AdminApi {
    // TODO: Need a paginated response - Update openApi as well
    @PreAuthorize("hasAuthority('Admin')")
    override suspend fun listUsers(): ResponseEntity<List<User>> = ResponseEntity.ok(userService.getUsers())
}
