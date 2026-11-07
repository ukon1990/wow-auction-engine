package net.jonasmf.auctionengine.profile

import net.jonasmf.auctionengine.config.MVCIntegrationTest
import net.jonasmf.auctionengine.testsupport.ProfessionProfileTestFixtures
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ProfileControllerIntegrationTest : MVCIntegrationTest() {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val owner = Actor(subject = "user-a")
    private val otherUser = Actor(subject = "user-b")

    @Test
    fun `profile endpoints reject anonymous callers`() {
        mvcGet("/api/profile/characters", actor = null).andExpect(status().isUnauthorized)
    }

    @Test
    fun `user cannot read another users character`() {
        val tree = ProfessionProfileTestFixtures.seedMinimalProfessionTree(jdbcTemplate)
        val characterId = ProfessionProfileTestFixtures.insertOwnedCharacter(jdbcTemplate, "user-a", "Owner")

        mvcGet("/api/profile/characters/$characterId/professions/${tree.professionId}", otherUser)
            .andExpect(status().isNotFound)
    }

    @Test
    fun `returns an empty allocation before a profile is saved`() {
        val tree = ProfessionProfileTestFixtures.seedMinimalProfessionTree(jdbcTemplate)
        val characterId = ProfessionProfileTestFixtures.insertOwnedCharacter(jdbcTemplate, "user-a", "Owner")

        mvcGet("/api/profile/characters/$characterId/professions/${tree.professionId}", owner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.allocations", hasSize<Any>(0)))
    }

    @Test
    fun `user creates character saves allocations and reads them back`() {
        val tree = ProfessionProfileTestFixtures.seedMinimalProfessionTree(jdbcTemplate)
        val character = mapOf("region" to "eu", "realmName" to "Argent Dawn", "characterName" to "Crafter")

        val created =
            mvcPost("/api/profile/characters", character, owner)
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.characterName").value("Crafter"))
                .andReturn()
        val characterId = objectMapper.readTree(created.response.contentAsString).get("id").asLong()
        val profilePath = "/api/profile/characters/$characterId/professions/${tree.professionId}"
        val profile =
            mapOf(
                "treeId" to tree.treeId,
                "skillLevel" to 85,
                "allocations" to listOf(mapOf("entryId" to tree.entryId, "rank" to 5)),
            )

        mvcPut(profilePath, profile, owner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.allocations", hasSize<Any>(1)))
            .andExpect(jsonPath("$.allocations[0].rank").value(5))
        mvcGet(profilePath, owner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.allocations[0].entryId").value(tree.entryId))
            .andExpect(jsonPath("$.allocations[0].rank").value(5))
        mvcGet("/api/profile/characters", owner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].characterName").value("Crafter"))
        mvcGet("/api/profile/characters", otherUser)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
    }
}
