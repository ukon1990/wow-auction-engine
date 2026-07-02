package net.jonasmf.auctionengine.testsupport

import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.okJson
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono
import java.net.URI

object BlizzardFixtures {
    fun handleRequest(request: ClientRequest): Mono<ClientResponse> {
        validateBlizzardRequest(request.url())

        if (request.url().path.endsWith("/connected-realm/index")) {
            return okJson(connectedRealmIndex())
        }

        val fixturePath = fixturePathFor(request)
        val body = loadFixture(this, fixturePath)
        return okJson(
            body = body,
            lastModified = AUCTION_LAST_MODIFIED.takeIf { fixturePath == AUCTION_METADATA_FIXTURE },
        )
    }

    private fun fixturePathFor(request: ClientRequest): String {
        val uri = request.url()
        val blizzardPath =
            uri.path
                .substringAfter("/data/wow/", missingDelimiterValue = uri.path.trimStart('/'))
                .trim('/')

        val resourcePath =
            when {
                blizzardPath == "profession" || blizzardPath == "profession/index" -> {
                    "profession/index"
                }

                blizzardPath == "modified-crafting" || blizzardPath == "modified-crafting/index" -> {
                    "modified-crafting/index"
                }

                blizzardPath == "modified-crafting/category" || blizzardPath == "modified-crafting/category/index" -> {
                    "modified-crafting/category/index"
                }

                blizzardPath == "modified-crafting/reagent-slot-type" ||
                    blizzardPath == "modified-crafting/reagent-slot-type/index" -> {
                    "modified-crafting/reagent-slot-type/index"
                }

                blizzardPath == "connected-realm/index" -> {
                    "connected-realm/index"
                }

                blizzardPath.matches(Regex("connected-realm/\\d+")) -> {
                    "connected-realm/connected-realm"
                }

                blizzardPath.matches(Regex("connected-realm/\\d+/auctions(/index)?")) -> {
                    auctionFixturePath(request)
                }

                blizzardPath == "auctions/commodities" -> {
                    auctionFixturePath(request)
                }

                blizzardPath.startsWith("media/") -> error("Unsupported Blizzard media fixture route: $uri")
                blizzardPath.matches(Regex("item/\\d+")) -> blizzardPath
                blizzardPath.matches(Regex("item-class/\\d+")) -> blizzardPath
                blizzardPath.matches(Regex("item-class/\\d+/item-subclass/\\d+")) -> blizzardPath
                blizzardPath.matches(Regex("item-appearance/\\d+")) -> blizzardPath
                blizzardPath.matches(Regex("profession/\\d+")) -> blizzardPath
                blizzardPath.matches(Regex("profession/\\d+/skill-tier/\\d+")) -> blizzardPath
                blizzardPath.matches(Regex("recipe/\\d+")) -> blizzardPath
                blizzardPath.matches(Regex("modified-crafting/category/\\d+")) -> blizzardPath
                blizzardPath.matches(Regex("modified-crafting/reagent-slot-type/\\d+")) -> blizzardPath
                else -> error("Unexpected Blizzard request path: $uri")
            }

        return "/blizzard/$resourcePath-response.json"
    }

    private fun auctionFixturePath(request: ClientRequest): String =
        if (request.headers().containsHeader(HttpHeaders.IF_MODIFIED_SINCE)) {
            "auction/auction-dump-metadata"
        } else {
            "auction/auction-data"
        }

    private fun validateBlizzardRequest(uri: URI) {
        require(uri.scheme == "https") {
            "Unexpected Blizzard request scheme: $uri"
        }
        require(uri.host in ALLOWED_BLIZZARD_HOSTS) {
            "Unexpected Blizzard request host: $uri"
        }
    }

    private fun connectedRealmIndex(): String =
        """
        {
          "connected_realms": [
            {
              "href": "https://eu.api.blizzard.test/data/wow/connected-realm/42?namespace=dynamic-eu"
            }
          ]
        }
        """.trimIndent()

    private val ALLOWED_BLIZZARD_HOSTS =
        setOf(
            "us.api.blizzard.com",
            "eu.api.blizzard.com",
            "kr.api.blizzard.com",
            "tw.api.blizzard.com",
            "us.api.blizzard.test",
            "eu.api.blizzard.test",
            "kr.api.blizzard.test",
            "tw.api.blizzard.test",
        )

    private const val AUCTION_METADATA_FIXTURE = "/blizzard/auction/auction-dump-metadata-response.json"
    private const val AUCTION_LAST_MODIFIED = "Wed, 01 Jan 2025 00:00:00 GMT"
}
