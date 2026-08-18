package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.controller.admin.AdminController
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/**
 * A helper for writing integration tests for MVC(controllers).
 * It uses test containers for storage.
 *
 * Note: Remember to declare @WebMvcTest(AdminController::class)
 */
@ImportAutoConfiguration(
    ServletWebSecurityAutoConfiguration::class,
    SecurityFilterAutoConfiguration::class,
)
@Import(SecurityConfig::class)
@TestPropertySource(
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
    ],
)
abstract class MVCIntegrationTest : IntegrationTestBase()
