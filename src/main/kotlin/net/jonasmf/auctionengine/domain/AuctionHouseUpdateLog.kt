package net.jonasmf.auctionengine.domain

import kotlin.time.Instant

class AuctionHouseUpdateLog(
    val id: Int,
    val lastModified: Instant,
    val size: Double,
    val timeSincePreviousDump: Long,
    val url: String,
)
