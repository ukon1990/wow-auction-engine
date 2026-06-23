package net.jonasmf.auctionengine.controller

import kotlinx.coroutines.runBlocking
import net.jonasmf.auctionengine.config.SecurityConfig
import net.jonasmf.auctionengine.generated.model.AdminConnectionStatus
import net.jonasmf.auctionengine.generated.model.AdminServerStatus
import net.jonasmf.auctionengine.generated.model.AdminStatus
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.service.admin.AdminStatusService
import net.jonasmf.auctionengine.service.admin.UserService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminController::class)
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
class AdminControllerTest {
    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var adminStatusService: AdminStatusService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var cognitoGroupsGrantedAuthoritiesConverter: Converter<Jwt, Collection<GrantedAuthority>>

    @Nested
    inner class GetAdminStatus {
        @Autowired
        private lateinit var mockMvc: MockMvc

        @Test
        fun `should return admin status if Cognito Admin group`() {
            `when`(adminStatusService.getStatus()).thenReturn(
                AdminStatus(
                    connections =
                        AdminConnectionStatus(
                            maxUsedConnections = 2,
                            threadsConnected = 1,
                            uptimeSeconds = 60,
                        ),
                    server =
                        AdminServerStatus(
                            usedMemoryMb = 128,
                            totalMemoryMb = 256,
                            freeMemoryMb = 128,
                            maxMemoryMb = 512,
                        ),
                    runningQueries = emptyList(),
                    tableSizes = emptyList(),
                ),
            )

            val result =
                mockMvc
                    .perform(
                        get("/api/admin/status")
                            .contextPath("/api")
                            .with(
                                jwt()
                                    .jwt { token ->
                                        token.claim("cognito:groups", listOf("admin"))
                                    }.authorities(cognitoGroupsGrantedAuthoritiesConverter),
                            ),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isOk)
        }

        @Test
        fun `should return 401 if unauthenticated`() {
            mockMvc
                .perform(get("/api/admin/status").contextPath("/api"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 if authenticated but not admin`() {
            val result =
                mockMvc
                    .perform(
                        get("/api/admin/status")
                            .contextPath("/api")
                            .with(jwt()),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class ListUsers {
        @Autowired
        private lateinit var mockMvc: MockMvc

        @Test
        fun `should return list of users if Cognito Admin group`() {
            runBlocking {
                `when`(userService.getUsers()).thenReturn(emptyList<User>())
            }

            val result =
                mockMvc
                    .perform(
                        get("/api/admin/users")
                            .contextPath("/api")
                            .with(
                                jwt()
                                    .jwt { token ->
                                        token.claim("cognito:groups", listOf("admin"))
                                    }.authorities(cognitoGroupsGrantedAuthoritiesConverter),
                            ),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isOk)
        }

        @Test
        fun `should return 401 if unauthenticated`() {
            mockMvc
                .perform(get("/api/admin/users").contextPath("/api"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 if authenticated but not admin`() {
            val result =
                mockMvc
                    .perform(
                        get("/api/admin/users")
                            .contextPath("/api")
                            .with(jwt()),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isForbidden)
        }
    }
}
