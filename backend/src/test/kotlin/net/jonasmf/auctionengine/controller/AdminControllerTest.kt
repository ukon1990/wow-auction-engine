package net.jonasmf.auctionengine.controller

import kotlinx.coroutines.runBlocking
import net.jonasmf.auctionengine.config.SecurityConfig
import net.jonasmf.auctionengine.generated.model.AdminExpansion1
import net.jonasmf.auctionengine.generated.model.GameLocale
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.generated.model.AdminConnectionStatus
import net.jonasmf.auctionengine.generated.model.AdminServerStatus
import net.jonasmf.auctionengine.generated.model.AdminItem1
import net.jonasmf.auctionengine.generated.model.AdminItemFields
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemPage
import net.jonasmf.auctionengine.generated.model.AdminSqlColumn
import net.jonasmf.auctionengine.generated.model.AdminSqlExecuteRequest
import net.jonasmf.auctionengine.generated.model.AdminSqlIndex
import net.jonasmf.auctionengine.generated.model.AdminSqlMetadata
import net.jonasmf.auctionengine.generated.model.AdminSqlResult
import net.jonasmf.auctionengine.generated.model.AdminStatus
import net.jonasmf.auctionengine.generated.model.AdminSqlTable
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionInspection
import net.jonasmf.auctionengine.service.admin.AdminExpansionService
import net.jonasmf.auctionengine.service.admin.AdminItemService
import net.jonasmf.auctionengine.service.admin.AdminJobService
import net.jonasmf.auctionengine.service.admin.AdminProfessionSyncService
import net.jonasmf.auctionengine.service.admin.AdminRecipeService
import net.jonasmf.auctionengine.service.admin.AdminSqlService
import net.jonasmf.auctionengine.service.admin.AdminStatusService
import net.jonasmf.auctionengine.service.admin.ProfessionTalentTreeImportService
import net.jonasmf.auctionengine.service.admin.NormalizedAuctionHelperProfessionInspectionService
import net.jonasmf.auctionengine.service.admin.UserService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpStatus
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

private val normalizedProfessionJson =
    """
    {
      "contractVersion": 1,
      "source": {
        "addon": "AuctionHelper",
        "processorVersion": "1.0.0",
        "files": [{"fileName": "AuctionHelper_Professions.lua", "sha256": "${"a".repeat(64)}"}]
      },
      "characters": []
    }
    """.trimIndent()

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
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var adminStatusService: AdminStatusService

    @MockitoBean
    private lateinit var adminSqlService: AdminSqlService

    @MockitoBean
    private lateinit var adminExpansionService: AdminExpansionService

    @MockitoBean
    private lateinit var adminJobService: AdminJobService

    @MockitoBean
    private lateinit var adminProfessionSyncService: AdminProfessionSyncService

    @MockitoBean
    private lateinit var adminItemService: AdminItemService

    @MockitoBean
    private lateinit var adminRecipeService: AdminRecipeService

    @MockitoBean
    private lateinit var professionTalentTreeImportService: ProfessionTalentTreeImportService

    @MockitoBean
    private lateinit var normalizedAuctionHelperProfessionInspectionService: NormalizedAuctionHelperProfessionInspectionService

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
    inner class InspectNormalizedAuctionHelperProfessionData {
        @Test
        fun `returns a preview without importing for an administrator`() {
            `when`(normalizedAuctionHelperProfessionInspectionService.inspect(any())).thenReturn(
                NormalizedAuctionHelperProfessionInspection(false, 1, 1, 1, 1, 0, 0, 0, emptyList()),
            )

            val result =
                mockMvc
                    .perform(
                        post("/api/admin/profession-talent-trees/inspect-normalized")
                            .contextPath("/api")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(normalizedProfessionJson)
                            .with(
                                jwt()
                                    .jwt { token -> token.claim("cognito:groups", listOf("admin")) }
                                    .authorities(cognitoGroupsGrantedAuthoritiesConverter),
                            ),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.imported").value(false))
                .andExpect(jsonPath("$.charactersFound").value(1))
                .andExpect(jsonPath("$.recipesWithOutputItemFound").value(1))
        }

        @Test
        fun `rejects an unauthenticated preview`() {
            mockMvc
                .perform(
                    post("/api/admin/profession-talent-trees/inspect-normalized")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(normalizedProfessionJson),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `rejects a non administrator preview`() {
            val result =
                mockMvc
                    .perform(
                        post("/api/admin/profession-talent-trees/inspect-normalized")
                            .contextPath("/api")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(normalizedProfessionJson)
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
        fun `should start profession sync job if Cognito Admin group`() {
            `when`(adminProfessionSyncService.syncProfessionRecipes("user")).thenReturn(
                AdminJob(
                    id = 2,
                    domain = AdminJob.Domain.PROFESSION,
                    operation = "sync-professions",
                    status = AdminJob.Status.RUNNING,
                    startedAt = OffsetDateTime.parse("2026-06-23T08:00:00Z"),
                    requestedBy = "user",
                ),
            )

            val result =
                mockMvc
                    .perform(
                        post("/api/admin/professions/sync")
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
                .andExpect(jsonPath("$.operation").value("sync-professions"))
        }

        @Test
        fun `should reject profession sync job for authenticated non-admin`() {
            val result =
                mockMvc
                    .perform(
                        post("/api/admin/professions/sync")
                            .contextPath("/api")
                            .with(jwt()),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should reject profession sync job when unauthenticated`() {
            mockMvc
                .perform(post("/api/admin/professions/sync").contextPath("/api"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return conflict when profession sync is already running`() {
            `when`(adminProfessionSyncService.syncProfessionRecipes("user")).thenThrow(
                ResponseStatusException(HttpStatus.CONFLICT, "Profession/recipe sync is already running"),
            )

            val result =
                mockMvc
                    .perform(
                        post("/api/admin/professions/sync")
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
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.detail").value("Profession/recipe sync is already running"))
        }

        @Test
        fun `should return active profession sync job for Cognito Admin group`() {
            `when`(adminJobService.getActiveProfessionSyncJob()).thenReturn(
                AdminJob(
                    id = 2,
                    domain = AdminJob.Domain.PROFESSION,
                    operation = "sync-professions",
                    status = AdminJob.Status.RUNNING,
                    startedAt = OffsetDateTime.parse("2026-06-23T08:00:00Z"),
                ),
            )

            val result =
                mockMvc
                    .perform(
                        get("/api/admin/professions/sync/active")
                            .contextPath("/api")
                            .with(
                                jwt()
                                    .jwt { token -> token.claim("cognito:groups", listOf("admin")) }
                                    .authorities(cognitoGroupsGrantedAuthoritiesConverter),
                            ),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(2))
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
        fun `should search admin items if Cognito Admin group`() {
            `when`(adminItemService.searchItems("cloth", null, null, true, 7, 1, null, null, 1, 25)).thenReturn(
                AdminItemPage(
                    items =
                        listOf(
                            AdminItem1(
                                id = 171374,
                                hasBase = true,
                                hasOverride = true,
                                effective =
                                    AdminItemFields(
                                        id = 171374,
                                        name = "Override cloth",
                                    ),
                            ),
                        ),
                    page = PageMetadata(page = 1, pageSize = 25, totalItems = 1, totalPages = 1),
                ),
            )

            val result =
                mockMvc
                    .perform(
                        get("/api/admin/items")
                            .contextPath("/api")
                            .param("query", "cloth")
                            .param("hasOverride", "true")
                            .param("itemClassId", "7")
                            .param("itemSubclassId", "1")
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
                .andExpect(jsonPath("$.items[0].id").value(171374))
                .andExpect(jsonPath("$.items[0].hasOverride").value(true))
        }

        @Test
        fun `should upsert sparse item override if Cognito Admin group`() {
            val response =
                AdminItem1(
                    id = 171374,
                    hasBase = true,
                    hasOverride = true,
                    effective = AdminItemFields(id = 171374, mediaUrl = "https://example.test/icon.png"),
                )
            `when`(
                adminItemService.upsertOverride(
                    171374,
                    AdminItemOverrideRequest(mediaUrl = "https://example.test/icon.png"),
                ),
            ).thenReturn(response)

            val result =
                mockMvc
                    .perform(
                        put("/api/admin/items/171374/override")
                            .contextPath("/api")
                            .contentType("application/json")
                            .content("""{"mediaUrl":"https://example.test/icon.png"}""")
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
                .andExpect(jsonPath("$.effective.mediaUrl").value("https://example.test/icon.png"))
        }

        @Test
        fun `should delete item override if Cognito Admin group`() {
            val result =
                mockMvc
                    .perform(
                        delete("/api/admin/items/171374/override")
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
                .andExpect(status().isNoContent)
        }
    }
}
