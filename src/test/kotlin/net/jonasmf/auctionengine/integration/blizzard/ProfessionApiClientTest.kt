package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Test

class ProfessionApiClientTest {
    @Test
    fun getAll() {
        // TODO
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
