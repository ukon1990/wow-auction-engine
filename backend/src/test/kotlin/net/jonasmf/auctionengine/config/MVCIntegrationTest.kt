package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.controller.admin.AdminController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc

/**
 * A helper for writing integration tests for MVC(controllers).
 * It uses test containers for storage.
 *
 */
@AutoConfigureMockMvc
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
abstract class MVCIntegrationTest : IntegrationTestBase() {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @MockitoBean
    protected lateinit var jwtDecoder: JwtDecoder

    @Autowired
    protected lateinit var cognitoGroupsGrantedAuthoritiesConverter: Converter<Jwt, Collection<GrantedAuthority>>
}
