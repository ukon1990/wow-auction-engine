package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.buildWebClient
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.createSupport
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.okJson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono

class CharacterProfessionApiClientTest {
    @Test
    fun `requests character professions from the regional Blizzard profile namespace`() {
        val webClient =
            buildWebClient { request ->
                assertThat(request.url().rawPath).isEqualTo("/data/wow/profile/wow/character/draenor/T%C3%A0ur%C3%B2s/professions")
                assertThat(request.url().query).isEqualTo("namespace=profile-eu")
                okJson(
                    """
                    {
                      "primaries": [{
                        "profession": {"id": 164, "name": "Blacksmithing"},
                        "tiers": [{
                          "skill_points": 100,
                          "max_skill_points": 100,
                          "tier": {"id": 2822, "name": "Dragon Isles Blacksmithing"},
                          "known_recipes": [{"id": 367595, "name": "Primal Molten Longsword"}]
                        }]
                      }],
                      "secondaries": [{
                        "profession": {"id": 185, "name": "Cooking"},
                        "tiers": []
                      }]
                    }
                    """.trimIndent(),
                )
            }
        val client = CharacterProfessionApiClient(createSupport(webClient))

        val professions = client.getProfessions(Region.Europe, "draenor", "Tàuròs")

        assertThat(professions.primaries.single().tiers.single().knownRecipes.single().name).isEqualTo("Primal Molten Longsword")
        assertThat(professions.secondaries.single().profession.id).isEqualTo(185)
    }

    @Test
    fun `redacts character identity from Blizzard API failure diagnostics`() {
        val webClient =
            buildWebClient {
                Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST).body("invalid character").build())
            }
        val client = CharacterProfessionApiClient(createSupport(webClient))

        assertThatThrownBy {
            client.getProfessions(Region.Europe, "draenor", "SecretCharacter")
        }.isInstanceOfSatisfying(BlizzardApiClientException::class.java) { failure ->
            assertThat(failure.url).isEqualTo("https://eu.api.blizzard.test/data/wow/profile/wow/character/%7BrealmSlug%7D/%7BcharacterName%7D/professions?namespace=profile-eu")
            assertThat(failure.url).doesNotContain("draenor", "SecretCharacter")
        }
    }
}
