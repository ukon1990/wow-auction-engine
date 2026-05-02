package net.jonasmf.auctionengine.dto.realm

import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.dto.Href

data class ConnectedRealmIndex(
    @JsonProperty("connected_realms")
    val connectedRealms: List<Href>,
)
