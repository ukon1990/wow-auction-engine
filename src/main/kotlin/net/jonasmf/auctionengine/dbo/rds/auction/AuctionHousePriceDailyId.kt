package net.jonasmf.auctionengine.dbo.rds.auction

import java.io.Serializable
import java.time.LocalDate

class AuctionHousePriceDailyId(
    var connectedRealmId: Int = 0,
    var date: LocalDate? = null,
    var itemId: Int = 0,
    var petSpeciesId: Int = 0,
    var modifierKey: String = "",
    var bonusKey: String = "",
) : Serializable
