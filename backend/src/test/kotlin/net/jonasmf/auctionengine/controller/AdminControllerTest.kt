package net.jonasmf.auctionengine.controller

import kotlinx.coroutines.runBlocking
import net.jonasmf.auctionengine.config.SecurityConfig
import net.jonasmf.auctionengine.generated.model.AdminExpansion1
import net.jonasmf.auctionengine.generated.model.GameLocale
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.generated.model.AdminConnectionStatus
import net.jonasmf.auctionengine.generated.model.AdminServerStatus
import net.jonasmf.auctionengine.generated.model.AdminSqlColumn
import net.jonasmf.auctionengine.generated.model.AdminSqlExecuteRequest
import net.jonasmf.auctionengine.generated.model.AdminSqlIndex
import net.jonasmf.auctionengine.generated.model.AdminSqlMetadata
import net.jonasmf.auctionengine.generated.model.AdminSqlResult
import net.jonasmf.auctionengine.generated.model.AdminStatus
import net.jonasmf.auctionengine.generated.model.AdminSqlTable
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.generated.model.AdminItem
import net.jonasmf.auctionengine.generated.model.AdminItemPage
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.service.admin.AdminExpansionService
import net.jonasmf.auctionengine.service.admin.AdminItemService
import net.jonasmf.auctionengine.service.admin.AdminJobService
import net.jonasmf.auctionengine.service.admin.AdminSqlService
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
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
    private lateinit var adminSqlService: AdminSqlService

    @MockitoBean
    private lateinit var adminExpansionService: AdminExpansionService

    @MockitoBean
    private lateinit var adminItemService: AdminItemService

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
    inner class ExecuteAdminSql {
        @Autowired
        private lateinit var mockMvc: MockMvc

        @Test
        fun `should execute sql diagnostics if Cognito Admin group`() {
            `when`(
                adminSqlService.execute(
                    AdminSqlExecuteRequest(
                        sql = "SELECT 1",
                        mode = AdminSqlExecuteRequest.Mode.QUERY,
                        limitRows = true,
                        rowLimit = 500,
                    ),
                ),
            ).thenReturn(
                AdminSqlResult(
                    mode = AdminSqlResult.Mode.QUERY,
                    effectiveSql = "SELECT 1 LIMIT 500",
                    columns = listOf("1"),
                    rows = listOf(listOf("1")),
                    rowCount = 1,
                    truncated = false,
                    durationMs = 2,
                ),
            )

            val result =
                mockMvc
                    .perform(
                        post("/api/admin/sql/execute")
                            .contextPath("/api")
                            .contentType("application/json")
                            .content(
                                """
                                {
                                  "sql": "SELECT 1",
                                  "mode": "QUERY",
                                  "limitRows": true,
                                  "rowLimit": 500
                                }
                                """.trimIndent(),
                            ).with(
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
                .andExpect(jsonPath("$.effectiveSql").value("SELECT 1 LIMIT 500"))
        }

        @Test
        fun `should return 400 for invalid sql`() {
            `when`(
                adminSqlService.execute(
                    AdminSqlExecuteRequest(
                        sql = "DELETE FROM auction",
                        mode = AdminSqlExecuteRequest.Mode.QUERY,
                        limitRows = true,
                        rowLimit = null,
                    ),
                ),
            ).thenThrow(ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "DELETE statements are not allowed"))

            val result =
                mockMvc
                    .perform(
                        post("/api/admin/sql/execute")
                            .contextPath("/api")
                            .contentType("application/json")
                            .content(
                                """
                                {
                                  "sql": "DELETE FROM auction",
                                  "mode": "QUERY",
                                  "limitRows": true
                                }
                                """.trimIndent(),
                            ).with(
                                jwt()
                                    .jwt { token ->
                                        token.claim("cognito:groups", listOf("admin"))
                                    }.authorities(cognitoGroupsGrantedAuthoritiesConverter),
                            ),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.detail").value("DELETE statements are not allowed"))
        }

        @Test
        fun `should return sql execution error detail in body`() {
            `when`(
                adminSqlService.execute(
                    AdminSqlExecuteRequest(
                        sql = "SELECT * FROM items",
                        mode = AdminSqlExecuteRequest.Mode.QUERY,
                        limitRows = true,
                        rowLimit = null,
                    ),
                ),
            ).thenThrow(
                ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Table 'dbo.items' doesn't exist",
                ),
            )

            val result =
                mockMvc
                    .perform(
                        post("/api/admin/sql/execute")
                            .contextPath("/api")
                            .contentType("application/json")
                            .content(
                                """
                                {
                                  "sql": "SELECT * FROM items",
                                  "mode": "QUERY",
                                  "limitRows": true
                                }
                                """.trimIndent(),
                            ).with(
                                jwt()
                                    .jwt { token ->
                                        token.claim("cognito:groups", listOf("admin"))
                                    }.authorities(cognitoGroupsGrantedAuthoritiesConverter),
                            ),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.detail").value("Table 'dbo.items' doesn't exist"))
        }

        @Test
        fun `should return 401 if unauthenticated`() {
            mockMvc
                .perform(
                    post("/api/admin/sql/execute")
                        .contextPath("/api")
                        .contentType("application/json")
                        .content("""{"sql":"SELECT 1","mode":"QUERY","limitRows":true}"""),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 if authenticated but not admin`() {
            val result =
                mockMvc
                    .perform(
                        post("/api/admin/sql/execute")
                            .contextPath("/api")
                            .contentType("application/json")
                            .content("""{"sql":"SELECT 1","mode":"QUERY","limitRows":true}""")
                            .with(jwt()),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should return sql metadata if Cognito Admin group`() {
            `when`(adminSqlService.getMetadata()).thenReturn(
                AdminSqlMetadata(
                    tables =
                        listOf(
                            AdminSqlTable(
                                name = "auction",
                                columns =
                                    listOf(
                                        AdminSqlColumn(
                                            name = "id",
                                            dataType = "bigint",
                                            nullable = false,
                                            ordinalPosition = 1,
                                        ),
                                    ),
                                indexes =
                                    listOf(
                                        AdminSqlIndex(
                                            name = "PRIMARY",
                                            unique = true,
                                            columns = listOf("id"),
                                        ),
                                    ),
                                engine = "InnoDB",
                                tableRows = 12,
                            ),
                        ),
                ),
            )

            val result =
                mockMvc
                    .perform(
                        get("/api/admin/sql/metadata")
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
                .andExpect(jsonPath("$.tables[0].name").value("auction"))
                .andExpect(jsonPath("$.tables[0].columns[0].name").value("id"))
                .andExpect(jsonPath("$.tables[0].indexes[0].name").value("PRIMARY"))
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

    @Nested
    inner class ItemAdmin {
        @Autowired
        private lateinit var mockMvc: MockMvc

        @Test
        fun `should list admin items if Cognito Admin group`() {
            `when`(
                adminItemService.listItems(
                    page = 0,
                    pageSize = 50,
                    itemId = null,
                    name = null,
                    qualityId = null,
                    classId = null,
                    subclassId = null,
                    expansionId = null,
                    hasOverride = null,
                    sort = "id",
                    locale = null,
                ),
            ).thenReturn(
                AdminItemPage(
                    items =
                        listOf(
                            AdminItem(
                                id = 19019,
                                hasOverride = false,
                                hasBase = true,
                                name = "Healing Potion",
                            ),
                        ),
                    page = PageMetadata(page = 0, pageSize = 50, totalItems = 1, totalPages = 1),
                ),
            )

            val result =
                mockMvc
                    .perform(
                        get("/api/admin/items")
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
                .andExpect(jsonPath("$.items[0].id").value(19019))
        }

        @Test
        fun `should return 403 for item admin endpoint if authenticated but not admin`() {
            val result =
                mockMvc
                    .perform(
                        get("/api/admin/items")
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
