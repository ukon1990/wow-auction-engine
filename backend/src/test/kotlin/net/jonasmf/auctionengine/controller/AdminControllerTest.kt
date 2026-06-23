package net.jonasmf.auctionengine.controller

import kotlinx.coroutines.runBlocking
import net.jonasmf.auctionengine.config.SecurityConfig
import net.jonasmf.auctionengine.generated.model.AdminExpansion1
import net.jonasmf.auctionengine.generated.model.GameLocale
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.generated.model.AdminConnectionStatus
import net.jonasmf.auctionengine.generated.model.AdminServerStatus
import net.jonasmf.auctionengine.generated.model.AdminStatus
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.service.admin.AdminExpansionService
import net.jonasmf.auctionengine.service.admin.AdminJobService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime

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
    private lateinit var adminExpansionService: AdminExpansionService

    @MockitoBean
    private lateinit var adminJobService: AdminJobService

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

    @Nested
    inner class ExpansionAdmin {
        @Autowired
        private lateinit var mockMvc: MockMvc

        @Test
        fun `should list expansions if Cognito Admin group`() {
            `when`(adminExpansionService.listExpansions(null)).thenReturn(
                listOf(
                    AdminExpansion1(
                        id = 1,
                        slug = "vanilla",
                        name = "Vanilla",
                        nameLocales = GameLocale(enUS = "Vanilla", enGB = "Vanilla"),
                        majorVersion = 1,
                        displayOrder = 10,
                    ),
                ),
            )

            val result =
                mockMvc
                    .perform(
                        get("/api/admin/expansions")
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
        fun `should start apply job if Cognito Admin group`() {
            `when`(adminExpansionService.applyExpansionRanges("user")).thenReturn(
                AdminJob(
                    id = 1,
                    domain = AdminJob.Domain.ITEM,
                    operation = "apply-expansion-ranges",
                    status = AdminJob.Status.RUNNING,
                    startedAt = OffsetDateTime.parse("2026-06-23T08:00:00Z"),
                    requestedBy = "user",
                ),
            )

            val result =
                mockMvc
                    .perform(
                        post("/api/admin/expansion-ranges/apply")
                            .contextPath("/api")
                            .with(
                                jwt()
                                    .jwt { token ->
                                        token.subject("user")
                                        token.claim("cognito:groups", listOf("admin"))
                                    }.authorities(cognitoGroupsGrantedAuthoritiesConverter),
                            ),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isAccepted)
        }

        @Test
        fun `should return 403 for expansion admin endpoint if authenticated but not admin`() {
            val result =
                mockMvc
                    .perform(
                        get("/api/admin/expansions")
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
