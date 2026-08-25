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
                blizzardPath in INDEX_FIXTURE_PATHS -> INDEX_FIXTURE_PATHS.getValue(blizzardPath)
                CONNECTED_REALM_AUCTIONS_PATH.matches(blizzardPath) -> auctionFixturePath(request)
                blizzardPath == "auctions/commodities" -> {
                    auctionFixturePath(request)
                }
                blizzardPath.startsWith("media/") -> error("Unsupported Blizzard media fixture route: $uri")
                DETAIL_FIXTURE_PATHS.any { it.matches(blizzardPath) } -> blizzardPath
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

    private val INDEX_FIXTURE_PATHS =
        mapOf(
            "profession" to "profession/index",
            "profession/index" to "profession/index",
            "modified-crafting" to "modified-crafting/index",
            "modified-crafting/index" to "modified-crafting/index",
            "modified-crafting/category" to "modified-crafting/category/index",
            "modified-crafting/category/index" to "modified-crafting/category/index",
            "modified-crafting/reagent-slot-type" to "modified-crafting/reagent-slot-type/index",
            "modified-crafting/reagent-slot-type/index" to "modified-crafting/reagent-slot-type/index",
            "connected-realm/index" to "connected-realm/index",
        )
    private val CONNECTED_REALM_AUCTIONS_PATH = Regex("connected-realm/\\d+/auctions(/index)?")
    private val DETAIL_FIXTURE_PATHS =
        listOf(
            Regex("item/\\d+"),
            Regex("item-class/\\d+"),
            Regex("item-class/\\d+/item-subclass/\\d+"),
            Regex("item-appearance/\\d+"),
            Regex("profession/\\d+"),
            Regex("profession/\\d+/skill-tier/\\d+"),
            Regex("recipe/\\d+"),
            Regex("modified-crafting/category/\\d+"),
            Regex("modified-crafting/reagent-slot-type/\\d+"),
            Regex("connected-realm/\\d+"),
        )

    private const val AUCTION_METADATA_FIXTURE = "/blizzard/auction/auction-dump-metadata-response.json"
    private const val AUCTION_LAST_MODIFIED = "Wed, 01 Jan 2025 00:00:00 GMT"
}
