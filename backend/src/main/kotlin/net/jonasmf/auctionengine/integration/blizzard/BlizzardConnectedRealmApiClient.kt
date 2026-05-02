package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.dto.realm.ConnectedRealmDTO
import net.jonasmf.auctionengine.dto.realm.ConnectedRealmIndex
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

private const val CONNECTED_REALM_INDEX_PATH = "connected-realm/index"

@Component
class BlizzardConnectedRealmApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    private fun defaultLocaleFor(region: Region): String =
        when (region) {
            Region.NorthAmerica -> "en_US"
            Region.Europe -> "en_GB"
            Region.Korea -> "ko_KR"
            Region.Taiwan -> "zh_TW"
        }

    fun getConnectedRealmIndex(region: Region): Mono<ConnectedRealmIndex> {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path = CONNECTED_REALM_INDEX_PATH,
                namespace = blizzardApiSupport.dynamicNamespaceForRegion(region).value,
                locale = defaultLocaleFor(region),
            )

        return blizzardApiSupport
            .webClient()
            .get()
            .uri(uri)
            .retrieve()
            .bodyToMono(ConnectedRealmIndex::class.java)
    }

    fun getConnectedRealm(href: Href): Mono<ConnectedRealmDTO> =
        blizzardApiSupport
            .webClient()
            .get()
            .uri(href.href)
            .retrieve()
            .bodyToMono(ConnectedRealmDTO::class.java)

    fun getConnectedRealm(
        id: Int,
        region: Region,
    ): Mono<ConnectedRealmDTO> {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path = "connected-realm/$id",
                namespace = blizzardApiSupport.dynamicNamespaceForRegion(region).value,
                locale = defaultLocaleFor(region),
            )

        return blizzardApiSupport
            .webClient()
            .get()
            .uri(uri)
            .retrieve()
            .bodyToMono(ConnectedRealmDTO::class.java)
    }
}
