package net.jonasmf.auctionengine.domain

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.RealmDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.StatsDynamo
import kotlin.time.Instant

class AuctionHouse(
    var id: Int?,
    var region: Region = Region.Europe,
    var autoUpdate: Boolean = false,
    var avgDelay: Long = 0,
    var connectedId: Int = 0,
    var gameBuild: Int = 0,
    var highestDelay: Long = 0,
    var lastDailyPriceUpdate: Instant? = null,
    var lastHistoryDeleteEvent: Instant? = null,
    var lastHistoryDeleteEventDaily: Instant? = null,
    var lastModified: Instant? = null,
    var lastRequested: Instant? = null,
    var lastStatsInsert: Instant? = null,
    var lastTrendUpdateInitiation: Instant? = null,
    var lowestDelay: Long = 0,
    var nextUpdate: Instant? = null,
    var realms: List<RealmDynamo> = emptyList(),
    var realmSlugs: String = "",
    var size: Double = 0.0,
    var stats: StatsDynamo =
        StatsDynamo(
            lastModified = 0L,
            url = "",
        ),
    var updateAttempts: Int = 0,
    var url: String = "",
)
