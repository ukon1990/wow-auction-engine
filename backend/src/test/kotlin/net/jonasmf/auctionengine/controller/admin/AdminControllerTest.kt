package net.jonasmf.auctionengine.controller.admin

import kotlinx.coroutines.runBlocking
import net.jonasmf.auctionengine.config.MVCIntegrationTest
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.generated.model.AdminExpansionRequest
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminSqlExecuteRequest
import net.jonasmf.auctionengine.generated.model.GameLocale
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.service.admin.UserService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode

class AdminControllerTest : MVCIntegrationTest() {
    @MockitoBean
    private lateinit var userService: UserService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `admin endpoints reject anonymous callers and callers outside the admin group`() {
        val calls: List<(Actor?) -> ResultActions> =
            listOf(
                { mvcGet("/api/admin/status", it) },
                { mvcGet("/api/admin/users", it) },
                { mvcPost("/api/admin/sql/execute", sql("SELECT 1"), it) },
                { mvcGet("/api/admin/expansions", it) },
                { mvcPost("/api/admin/professions/sync", actor = it) },
                { mvcGet("/api/admin/items", it) },
                { mvcPost("/api/admin/profession-talent-trees/inspect-normalized", normalizedImport(), it) },
            )

        calls.forEach { call ->
            call(null).andExpect(status().isUnauthorized)
            call(user).andExpect(status().isForbidden)
        }
    }

    @Test
    fun `returns live database status`() {
        mvcGet("/api/admin/status", admin)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connections.threadsConnected").isNumber)
            .andExpect(jsonPath("$.tableSizes").isArray)
    }

    @Test
    fun `lists Cognito users`() {
        runBlocking { `when`(userService.getUsers()).thenReturn(listOf(User(sub = "sub-1", username = "jonas"))) }

        mvcGet("/api/admin/users", admin)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].username").value("jonas"))
    }

    @Nested
    inner class Sql {
        @Test
        fun `runs a query with the default row limit`() {
            mvcPost("/api/admin/sql/execute", sql("SELECT 1"), admin)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.effectiveSql").value("SELECT 1 LIMIT 500"))
                .andExpect(jsonPath("$.rows[0][0]").value("1"))
        }

        @Test
        fun `rejects destructive statements`() {
            mvcPost("/api/admin/sql/execute", sql("DELETE FROM auction"), admin)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.detail").value("DELETE statements are not allowed"))
        }

        @Test
        fun `returns the database error for an invalid query`() {
            mvcPost("/api/admin/sql/execute", sql("SELECT * FROM items"), admin)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.detail").value(containsString("items")))
        }

        @Test
        fun `describes tables`() {
            mvcGet("/api/admin/sql/metadata", admin)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.tables[*].name", hasItem("item")))
        }
    }

    @Nested
    inner class Expansions {
        @Test
        fun `creates updates and deletes an expansion with an item range`() {
            mvcPost("/api/admin/expansions", expansion(name = "Midnight"), admin)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.slug").value("midnight"))
            mvcPut("/api/admin/expansions/12", expansion(name = "Midnight (renamed)"), admin)
                .andExpect(status().isOk)
            mvcGet("/api/admin/expansions", admin)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].name").value("Midnight (renamed)"))

            val rangeId =
                mvcPost("/api/admin/expansion-ranges", AdminExpansionItemRangeRequest(12, 240_000, 250_000), admin)
                    .andExpect(status().isOk)
                    .body()
                    .path("id")
                    .asLong()
            mvcGet("/api/admin/expansion-ranges", admin)
                .andExpect(jsonPath("$[0].expansion.id").value(12))
                .andExpect(jsonPath("$[0].startItemId").value(240_000))

            mvcDelete("/api/admin/expansion-ranges/$rangeId", admin).andExpect(status().isNoContent)
            mvcDelete("/api/admin/expansions/12", admin).andExpect(status().isNoContent)
            mvcGet("/api/admin/expansions", admin).andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `applies expansion ranges as a background job`() {
            mvcPost("/api/admin/expansion-ranges/apply", actor = admin)
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.operation").value("apply-expansion-ranges"))
                .andExpect(jsonPath("$.requestedBy").value(admin.subject))
        }
    }

    @Nested
    inner class Items {
        @Test
        fun `searches overrides and restores an item`() {
            insertItem(id = 224_025, name = "Core Alloy")

            mvcGet("/api/admin/items?query=Alloy", admin)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].id").value(224_025))
                .andExpect(jsonPath("$.items[0].hasOverride").value(false))

            mvcPut(
                "/api/admin/items/224025/override",
                AdminItemOverrideRequest(mediaUrl = "https://example.test/icon.png"),
                admin,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.hasOverride").value(true))
                .andExpect(jsonPath("$.effective.mediaUrl").value("https://example.test/icon.png"))

            mvcDelete("/api/admin/items/224025/override", admin).andExpect(status().isNoContent)
            mvcGet("/api/admin/items/224025", admin)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.hasOverride").value(false))
        }

        @Test
        fun `rejects an override for an unknown item`() {
            mvcPut("/api/admin/items/1/override", AdminItemOverrideRequest(level = 10), admin)
                .andExpect(status().isNotFound)
        }

        @Test
        fun `requires an item class when filtering by subclass`() {
            mvcGet("/api/admin/items?itemSubclassId=1", admin).andExpect(status().isBadRequest)
            mvcGet("/api/admin/recipes?itemSubclassId=1", admin).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class Recipes {
        @Test
        fun `rejects an unsupported association type`() {
            mvcGet("/api/admin/recipes?associatedItemId=1&associationType=other", admin)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.detail").value("associationType must be crafted or reagent"))
        }

        @Test
        fun `returns 404 when deleting a missing override`() {
            mvcDelete("/api/admin/recipes/1/override", admin).andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class NormalizedProfessionImport {
        @Test
        fun `imports normalized AuctionHelper data`() {
            mvcPost("/api/admin/profession-talent-trees/inspect-normalized", normalizedImport(), admin)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.imported").value(true))
                .andExpect(jsonPath("$.charactersFound").value(0))
        }

        @Test
        fun `returns a concise validation error for oversized node names`() {
            mvcPost(
                "/api/admin/profession-talent-trees/inspect-normalized",
                normalizedImport(characters = oversizedNodeCharacter),
                admin,
            ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.detail").value(containsString("validation failed")))
        }
    }

    private fun sql(query: String) =
        AdminSqlExecuteRequest(sql = query, mode = AdminSqlExecuteRequest.Mode.QUERY, limitRows = true)

    private fun expansion(name: String) =
        AdminExpansionRequest(
            id = 12,
            slug = "midnight",
            majorVersion = 12,
            displayOrder = 120,
            nameLocales = GameLocale(enUS = name, enGB = name),
        )

    private fun normalizedImport(characters: String = "[]"): JsonNode =
        objectMapper.readTree(
            """
            {
              "contractVersion": 1,
              "source": {
                "addon": "AuctionHelper",
                "processorVersion": "1.0.0",
                "files": [{"fileName": "AuctionHelper_Professions.lua", "sha256": "${"a".repeat(64)}"}]
              },
              "characters": $characters
            }
            """.trimIndent(),
        )

    private val oversizedNodeCharacter =
        """
        [{
          "characterKey": "eu:test", "name": "Test", "realm": "Realm", "region": "eu",
          "professions": [{
            "professionId": 164, "skillLineId": 164, "name": "Blacksmithing", "recipes": [],
            "talents": {
              "trees": [{
                "treeId": 1, "skillLineId": 2907, "expansionId": 12,
                "tabs": [{"tabId": 1, "nodes": [{"nodeId": 1, "name": "${"x".repeat(129)}", "entries": [{"entryId": 1}]}]}]
              }],
              "allocations": []
            }
          }]
        }]
        """.trimIndent()

    private fun ResultActions.body(): JsonNode = objectMapper.readTree(andReturn().response.contentAsString)

    private fun insertItem(
        id: Int,
        name: String,
    ) {
        jdbcTemplate.update(
            "INSERT INTO locale (id, en_gb, en_us, source_type, source_key, source_field) VALUES (?, ?, ?, 'ITEM', ?, 'name')",
            id,
            name,
            name,
            id.toString(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, rank, max_count, purchase_price,
                purchase_quantity, required_level, sell_price, name_id, is_override
            ) VALUES (?, FALSE, TRUE, 1, 1, 200, 0, 1, 1, 0, ?, FALSE)
            """.trimIndent(),
            id,
            id,
        )
    }
}
