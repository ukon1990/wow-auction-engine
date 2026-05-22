package net.jonasmf.auctionengine.controller

import io.netty.util.concurrent.CompleteFuture
import net.jonasmf.auctionengine.generated.api.AdminApi
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.generated.model.Users
import net.jonasmf.auctionengine.service.admin.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

@RestController
class AdminController(
    val userService: UserService,
) : AdminApi {
    // TODO: Need a paginated response - Update openApi as well
    @PreAuthorize("hasRole('ADMIN')")
    override suspend fun listUsers(): ResponseEntity<List<User>> = ResponseEntity.ok(userService.getUsers())
}
