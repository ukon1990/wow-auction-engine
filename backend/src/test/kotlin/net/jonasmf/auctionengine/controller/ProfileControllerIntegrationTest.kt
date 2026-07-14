package net.jonasmf.auctionengine.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.ProfessionProfileTestFixtures
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
    ],
)
class ProfileControllerIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = jacksonObjectMapper()

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `profile endpoints reject anonymous callers`() {
        mockMvc
            .perform(get("/api/profile/characters").contextPath("/api"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `user cannot read another users character`() {
        val tree = ProfessionProfileTestFixtures.seedMinimalProfessionTree(jdbcTemplate)
        val characterId =
            ProfessionProfileTestFixtures.insertOwnedCharacter(
                jdbcTemplate,
                ownerSubject = "user-a",
                characterName = "Owner",
            )

        performAsync(
            mockMvc
                .perform(
                    get("/api/profile/characters/$characterId/professions/${tree.professionId}")
                        .contextPath("/api")
                        .with(jwt().jwt { it.subject("user-b") }),
                ).andReturn(),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `user creates character saves allocations and reads them back`() {
        val tree = ProfessionProfileTestFixtures.seedMinimalProfessionTree(jdbcTemplate)

        val createResult =
            performAsync(
                mockMvc
                    .perform(
                        post("/api/profile/characters")
                            .contextPath("/api")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    mapOf(
                                        "region" to "eu",
                                        "realmName" to "Argent Dawn",
                                        "characterName" to "Crafter",
                                    ),
                                ),
                            ).with(jwt().jwt { it.subject("user-a") }),
                    ).andReturn(),
            ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.characterName").value("Crafter"))
                .andReturn()

        val characterId = objectMapper.readTree(createResult.response.contentAsString).get("id").asLong()

        performAsync(
            mockMvc
                .perform(
                    put("/api/profile/characters/$characterId/professions/${tree.professionId}")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "treeId" to tree.treeId,
                                    "skillLevel" to 85,
                                    "allocations" to listOf(mapOf("entryId" to tree.entryId, "rank" to 5)),
                                ),
                            ),
                        ).with(jwt().jwt { it.subject("user-a") }),
                ).andReturn(),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.allocations", hasSize<Any>(1)))
            .andExpect(jsonPath("$.allocations[0].rank").value(5))

        performAsync(
            mockMvc
                .perform(
                    get("/api/profile/characters/$characterId/professions/${tree.professionId}")
                        .contextPath("/api")
                        .with(jwt().jwt { it.subject("user-a") }),
                ).andReturn(),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.allocations[0].entryId").value(tree.entryId))
            .andExpect(jsonPath("$.allocations[0].rank").value(5))

        performAsync(
            mockMvc
                .perform(
                    get("/api/profile/characters")
                        .contextPath("/api")
                        .with(jwt().jwt { it.subject("user-a") }),
                ).andReturn(),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].characterName").value("Crafter"))
    }

    private fun performAsync(result: MvcResult) = mockMvc.perform(asyncDispatch(result))
}
