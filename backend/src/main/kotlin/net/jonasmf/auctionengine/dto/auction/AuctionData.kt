package net.jonasmf.auctionengine.dto.auction

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.dto.Links

@JsonIgnoreProperties(ignoreUnknown = true)
data class AuctionData(
    @JsonProperty("_links")
    val _links: Links,
    val auctions: List<AuctionDTO>,
    @JsonProperty("commodities")
    val commodities: Href? = null,
)
