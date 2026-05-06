package net.jonasmf.auctionengine.controller

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminController {
    @GetMapping("/session-check")
    fun sessionCheck(authentication: Authentication): ResponseEntity<Unit> =
        if (authentication.isAuthenticated) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.status(401).build()
        }
}
