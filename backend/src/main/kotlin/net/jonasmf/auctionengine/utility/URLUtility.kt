package net.jonasmf.auctionengine.utility

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region

fun determineBaseUrl(
    region: Region,
    properties: BlizzardApiProperties,
): String =
    when (region) {
        Region.Europe -> "https://eu.${properties.baseUrl}"
        Region.NorthAmerica -> "https://us.${properties.baseUrl}"
        Region.Korea -> "https://kr.${properties.baseUrl}"
        Region.Taiwan -> "https://tw.${properties.baseUrl}"
    }
