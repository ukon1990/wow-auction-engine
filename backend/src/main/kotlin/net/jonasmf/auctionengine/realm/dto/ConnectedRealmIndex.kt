package net.jonasmf.auctionengine.realm.dto

import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.integration.blizzard.dto.Href

data class ConnectedRealmIndex(
    @JsonProperty("connected_realms")
    val connectedRealms: List<Href>,
)
