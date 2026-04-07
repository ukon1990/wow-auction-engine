package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.constant.Region
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "blizzard")
data class BlizzardApiProperties
    @ConstructorBinding
    constructor(
        val baseUrl: String,
        val tokenUrl: String,
        val clientId: String,
        val clientSecret: String,
        val region: Region? = null,
        val regions: List<Region> = emptyList(),
    ) {
        init {
            require(region != null || regions.isNotEmpty()) {
                "At least one Blizzard region must be configured"
            }
        }

        val configuredRegions: List<Region> =
            if (regions.isNotEmpty()) {
                regions
            } else {
                listOfNotNull(region)
            }

        val primaryRegion: Region
            get() = configuredRegions.first()
    }
