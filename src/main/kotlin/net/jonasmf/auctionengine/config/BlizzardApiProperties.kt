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
        val region: Region
    )
