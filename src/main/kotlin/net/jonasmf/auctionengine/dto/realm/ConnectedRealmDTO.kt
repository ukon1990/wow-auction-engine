package net.jonasmf.auctionengine.dto.realm

import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.constant.RealmPopulation
import net.jonasmf.auctionengine.constant.RealmStatus
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.dto.LocaleTypeValue
import java.time.ZonedDateTime

data class ConnectedRealmDTO(
    val id: Int,
    @JsonProperty("has_queue")
    val hasQueue: Boolean,
    val status: LocaleTypeValue<RealmStatus>,
    val population: LocaleTypeValue<RealmPopulation>,
    val realms: List<RealmDTO>,
    @JsonProperty("mythic_leaderboards")
    val mythicLeaderboards: Href,
    val auctions: Href,
) {
    fun toDBO(): ConnectedRealm =
        ConnectedRealm(
            id = id,
            realms = realms.map { it.toDBO() },
            auctionHouse =
                AuctionHouse(
                    lastModified = null,
                    lastRequested = null,
                    nextUpdate = ZonedDateTime.now(),
                    lowestDelay = 0,
                    averageDelay = 0,
                    highestDelay = 0,
                    tsmFile = null,
                    statsFile = null,
                    auctionFile = null,
                    failedAttempts = 0,
                    updateLog = emptyList(),
                ),
        )
}
