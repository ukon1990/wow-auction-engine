package net.jonasmf.auctionengine.domain.realm

import jdk.internal.org.jline.reader.LineReader
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import kotlin.time.Instant
import net.jonasmf.auctionengine.constant.Region as RegionConstant

class AuctionHouse(
    var id: Int?,
    var region: RegionConstant = RegionConstant.Europe,
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
    var lowestDelay: Long = 0,
    var nextUpdate: Instant? = null,
    var realms: List<Realm> = emptyList(),
    var updateAttempts: Int = 0,
)

class Realm(
    val id: Int = 0,
    val name: String,
    val slug: String,
    val region: Region,
    val category: String,
    val locale: Locale,
    val timezone: String,
    val gameBuild: GameBuildVersion,
)

class Region(
    val id: Int?,
    val name: String,
    val type: RegionConstant,
)
