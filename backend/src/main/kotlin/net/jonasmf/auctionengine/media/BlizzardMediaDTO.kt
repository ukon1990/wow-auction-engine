package net.jonasmf.auctionengine.media

import net.jonasmf.auctionengine.integration.blizzard.dto.Links
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class BlizzardMediaDTO(
    @JsonProperty("_links")
    val links: Links? = null,
    val assets: List<BlizzardMediaAssetDTO> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BlizzardMediaAssetDTO(
    val key: String,
    val value: String,
)
