package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.NameSpace
import net.jonasmf.auctionengine.constant.Region
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

/**
 * This is a helper for using blizzard API's.
 * It will fallback to North America if region is not provided, as I'm asuming this is where new patches setc
 * are released first.
 */
@Component
class BlizzardApiSupport(
    private val properties: BlizzardApiProperties,
    private val webClientWithAuth: WebClient,
) {
    fun webClient(): WebClient = webClientWithAuth

    fun buildRegionalUri(
        region: Region = getPropertyRegionOrFallback(),
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

    fun determineBaseUrl(region: Region = getPropertyRegionOrFallback()): String =
        "https://${region.code}.${properties.baseUrl.removePrefix("https://")}"

    fun dynamicNamespaceForRegion(region: Region = getPropertyRegionOrFallback()): NameSpace =
        NameSpace.getDynamicForRegion(region)

    fun namespaceForBuild(gameBuild: GameBuildVersion): NameSpace =
        when (gameBuild) {
            GameBuildVersion.CLASSIC -> NameSpace.DYNAMIC_CLASSIC
            GameBuildVersion.RETAIL -> NameSpace.DYNAMIC_RETAIL
        }

    private fun getPropertyRegionOrFallback(): Region = properties.region ?: Region.NorthAmerica
}
