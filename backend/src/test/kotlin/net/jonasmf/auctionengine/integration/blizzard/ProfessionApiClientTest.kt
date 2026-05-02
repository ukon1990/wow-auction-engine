package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.buildWebClient
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.createSupport
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.okJson
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono
import kotlin.test.assertEquals

class ProfessionApiClientTest {
    @Nested
    internal inner class GetAll {
        @Test
        fun `should return index response with all professions`() {
            val webClient = buildWebClient { handleRequest(it) }
            val client = ProfessionApiClient(createSupport(webClient))

            val professions = client.getAll(Region.Europe)

            assertEquals(4, professions.size)
            assertEquals(2, professions[0].skillTiers.size)
            assertEquals(
                6,
                professions[0]
                    .skillTiers[0]
                    .categories
                    .flatMap { it.recipes }
                    .size,
            )
        }
    }

    @Test
    fun `getById returns nested skill tiers and recipe stubs`() {
        val webClient = buildWebClient { handleRequest(it) }
        val client = ProfessionApiClient(createSupport(webClient))

        val profession = client.getById(356, Region.Europe)

        assertEquals(356, profession.id)
        assertEquals("Fishing", profession.name.en_US)
        assertEquals(2, profession.skillTiers.size)
        assertEquals(2911, profession.skillTiers[1].id)
        assertEquals(
            51965,
            profession.skillTiers[1]
                .categories[1]
                .recipes
                .first()
                .id,
        )
        assertEquals(
            null,
            profession.skillTiers[1]
                .categories[1]
                .recipes
                .first()
                .description,
        )
    }

    private fun professionIndexBody(): String = loadFixture(this, "/blizzard/profession/index-response.json")

    private fun professionById(id: Int): String = loadFixture(this, "/blizzard/profession/$id-response.json")

    private fun professionSkillTierById(
        professionId: Int,
        skillTierId: Int,
    ): String = loadFixture(this, "/blizzard/profession/$professionId/skill-tier/$skillTierId-response.json")

    fun handleRequest(request: ClientRequest): Mono<ClientResponse> {
        val path = request.url().path

        return when {
            path.endsWith(PROFESSION_INDEX_PATH) -> okJson(professionIndexBody())
            path.matches(Regex(".*/profession/\\d+$")) -> okJson(professionById(path.substringAfterLast('/').toInt()))
            path.matches(Regex(".*/profession/\\d+/skill-tier/\\d+$")) -> {
                val parts = path.split("/")
                val professionId = parts[parts.size - 3].toInt()
                val skillTierId = parts.last().toInt()
                okJson(professionSkillTierById(professionId, skillTierId))
            }
            else -> error("Unexpected request: ${request.method()} ${request.url()}")
        }
    }
}
