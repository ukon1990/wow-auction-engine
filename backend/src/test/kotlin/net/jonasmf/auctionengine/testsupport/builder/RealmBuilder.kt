package net.jonasmf.auctionengine.testsupport.builder

import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.domain.realm.AuctionHouse
import net.jonasmf.auctionengine.domain.realm.ConnectedRealm
import net.jonasmf.auctionengine.domain.realm.Realm
import kotlin.time.Instant
import net.jonasmf.auctionengine.constant.Region as RegionConstant
import net.jonasmf.auctionengine.domain.realm.Region as RealmRegion

fun buildRealm(
    id: Int = 1,
    name: String = "Test Realm",
    slug: String = "test-realm",
    region: RealmRegion = RealmRegion(id = 2, name = "Europe", type = RegionConstant.Europe),
    category: String = "Normal",
    locale: Locale = Locale.EN_GB,
    timezone: String = "UTC",
    gameBuild: GameBuildVersion = GameBuildVersion.RETAIL,
): Realm =
    Realm(
        id = id,
        name = name,
        slug = slug,
        region = region,
        category = category,
        locale = locale,
        timezone = timezone,
        gameBuild = gameBuild,
    )

fun buildAuctionHouse(
    id: Int = 1,
    connectedId: Int = 1,
    region: RegionConstant = RegionConstant.Europe,
    gameBuild: Int = 0,
    autoUpdate: Boolean = true,
    lowestDelay: Long = 60,
    avgDelay: Long = 60,
    highestDelay: Long = 60,
    lastModified: Instant? = null,
    nextUpdate: Instant? = null,
    lastRequested: Instant? = null,
    lastDailyPriceUpdate: Instant? = null,
    lastHistoryDeleteEvent: Instant? = null,
    lastAuctionPriceDeleteEvent: Instant? = null,
    lastHistoryDeleteEventDaily: Instant? = null,
    realms: MutableList<Realm> = mutableListOf(),
    updateAttempts: Int = 0,
): AuctionHouse =
    AuctionHouse(
        id = id,
        region = region,
        autoUpdate = autoUpdate,
        lowestDelay = lowestDelay,
        avgDelay = avgDelay,
        highestDelay = highestDelay,
        connectedId = connectedId,
        gameBuild = gameBuild,
        lastDailyPriceUpdate = lastDailyPriceUpdate,
        lastAuctionPriceDeleteEvent = lastAuctionPriceDeleteEvent,
        lastHistoryDeleteEvent = lastHistoryDeleteEvent,
        lastHistoryDeleteEventDaily = lastHistoryDeleteEventDaily,
        lastModified = lastModified,
        lastRequested = lastRequested,
        nextUpdate = nextUpdate,
        realms = realms,
        updateAttempts = updateAttempts,
    )

fun buildConnectedRealm(
    id: Int = 1,
    auctionHouses: AuctionHouse = buildAuctionHouse(),
    realms: MutableList<Realm> = mutableListOf(buildRealm()),
): ConnectedRealm =
    ConnectedRealm(
        id = id,
        auctionHouse = auctionHouses,
        realms = realms,
    )
