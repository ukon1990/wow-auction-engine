package net.jonasmf.auctionengine.auction.dto

import net.jonasmf.auctionengine.constant.GameBuildVersion

data class AuctionDataResponse(
    val lastModified: Long,
    val url: String,
    val gameBuild: GameBuildVersion,
)
