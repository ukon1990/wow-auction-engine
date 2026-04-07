package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.NameSpace
import net.jonasmf.auctionengine.constant.Region
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

@Component
class BlizzardApiSupport(
    private val properties: BlizzardApiProperties,
    private val webClientWithAuth: WebClient,
) {
    fun webClient(): WebClient = webClientWithAuth

    fun buildRegionalUri(
        region: Region,
        path: String,
        namespace: String? = null,
        locale: String? = null,
    ): String {
        val builder =
            UriComponentsBuilder
                .fromUriString(determineBaseUrl(region))
                .path(path.removePrefix("/"))

        if (namespace != null) {
            builder.queryParam("namespace", namespace)
        }

        if (locale != null) {
            builder.queryParam("locale", locale)
        }

        return builder.toUriString()
    }

    fun determineBaseUrl(region: Region): String =
        "https://${region.code}.${properties.baseUrl.removePrefix("https://")}"

    fun dynamicNamespaceForRegion(region: Region): NameSpace = NameSpace.getDynamicForRegion(region)

    fun namespaceForBuild(gameBuild: GameBuildVersion): NameSpace =
        when (gameBuild) {
            GameBuildVersion.CLASSIC -> NameSpace.DYNAMIC_CLASSIC
            GameBuildVersion.RETAIL -> NameSpace.DYNAMIC_RETAIL
        }
}
