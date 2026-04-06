package net.jonasmf.auctionengine.dbo.rds.auction

import java.io.Serializable
import java.time.LocalDate

data class AuctionHousePriceId(
    var connectedRealmId: Int = 0,
    var date: LocalDate? = null,
    var ahTypeId: Int = 0,
    var itemId: Int = 0,
    var petSpeciesId: Int = 0,
    var hourOfDay: Int = 0,
) : Serializable
