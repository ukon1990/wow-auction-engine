package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.buildWebClient
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.okJson
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono
import kotlin.test.assertEquals

class ProfessionApiClientTest(
    private val properties: BlizzardApiProperties,
) {
    @Nested
    internal inner class GetAll {
        @Test
        fun `should return a index response with all professions`() {
            val webClient =
                buildWebClient {
                    handleRequest(it)
                }
            val client =
                ProfessionApiClient(
                    BlizzardApiSupport(properties, webClient),
                )
            val response = client.getAll()

            assertEquals(2, response.professions.size)
        }
    }

    @Test
    fun getById() {
        // TODO
    }

    private fun professionIndexBody(): String = loadFixture(this, "blizzard/profession/index-response.json")

    private fun professionById(id: Int): String = loadFixture(this, "blizzard/profesion/details/$id-response.json")

    private fun professionSkillTierById(id: Int): String =
        loadFixture(this, "blizzard/profesion/skill-tier/$id-response.json")

    fun handleRequest(request: ClientRequest): Mono<ClientResponse> {
        val path = request.url().path

        return when {
            path.endsWith("/profession/index") -> {
                okJson(professionIndexBody())
            }

            path.matches(Regex(""".*/profession/\d+$""")) -> {
                okJson(professionById(path.substringAfterLast('/').toInt()))
            }

            path.matches(Regex(""".*/profession/\d+/skill-tier/\d+$""")) -> {
                okJson(professionSkillTierById(path.substringAfterLast('/').toInt()))
            }

            else -> {
                error("Unexpected request: ${request.method()} ${request.url()}")
            }
        }
    }
}
