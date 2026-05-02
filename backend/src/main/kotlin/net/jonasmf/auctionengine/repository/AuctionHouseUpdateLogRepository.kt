package net.jonasmf.auctionengine.repository

import net.jonasmf.auctionengine.domain.AuctionHouseUpdateLog
import kotlin.time.Instant

interface AuctionHouseUpdateLogRepository {
    fun findByIdAndMostRecentLastModified(connectedRealmId: Int): List<AuctionHouseUpdateLog>

    fun findNewestEntryForConnectedRealm(connectedRealmId: Int): AuctionHouseUpdateLog?

    fun save(
        connectedId: Int,
        lastModified: Instant,
        size: Double,
        url: String,
    ): AuctionHouseUpdateLog
}
