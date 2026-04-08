package net.jonasmf.auctionengine.dbo.rds.auction

import java.io.Serializable
import java.time.LocalDateTime

data class AuctionHousePriceId(
    var connectedRealmId: Int = 0,
    var auctionTimestamp: LocalDateTime? = null,
    var ahTypeId: Int = 0,
    var itemId: Int = 0,
    var petSpeciesId: Int = 0,
    var modifierKey: String = "",
    var bonusKey: String = "",
) : Serializable
