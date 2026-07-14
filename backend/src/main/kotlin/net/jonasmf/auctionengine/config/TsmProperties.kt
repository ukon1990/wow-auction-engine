package net.jonasmf.auctionengine.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.tsm")
data class TsmProperties(
    val publicDataBaseUrl: String,
) {
    init {
        require(publicDataBaseUrl.isNotBlank()) {
            "app.tsm.public-data-base-url must not be blank"
        }
    }
}
