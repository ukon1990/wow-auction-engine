package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.rds.auction.Auction
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionPrice
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.utility.AuctionVariantKeyUtility
import java.time.Instant

/**
 * Used when reading from the JSON, to flatten it to save memory
 */
class FlatAuction(
    val tempId: String,
    val auctionId: Long,
    val itemId: Int,
    var petSpeciesId: Int? = null,
    var petQualityId: Int? = null,
    var petLevel: Int? = null,
    var buyout: Long?,
    var bid: Long?,
    var quantity: Long,
)

fun AuctionDTO.toFlatObject() =
    FlatAuction(
        tempId = getUniqueId(),
        auctionId = id,
        itemId = item.id,
        petSpeciesId = item.pet_species_id,
        petQualityId = item.pet_quality_id,
        petLevel = item.pet_level,
        buyout = buyout ?: unit_price,
        bid = bid,
        quantity = quantity,
    )

fun FlatAuction.toDBO(
    connectedRealm: ConnectedRealm,
    updateHistory: ConnectedRealmUpdateHistory,
): Auction =
    Auction(
        id = null,
        connectedRealm = connectedRealm,
        itemId = itemId,
        petSpeciesId = petSpeciesId,
        petQualityId = petQualityId,
        petLevel = petLevel,
        prices =
            mutableListOf(
                toAuctionPriceDBO(updateHistory.lastModified?.toInstant()),
            ),
        quantity = quantity,
        buyout = buyout,
        bid = bid,
        p25 = null,
        p75 = null,
        firstSeen = null,
        lastSeen = null,
        updateHistory = updateHistory,
    )

fun FlatAuction.toAuctionPriceDBO(lastModified: Instant?): AuctionPrice =
    AuctionPrice(
        id = auctionId,
        buyout = buyout,
        bid = bid,
        quantity = quantity,
        lastModified = lastModified,
    )

fun AuctionDTO.getUniqueId(): String =
    "${item.id}-${item.pet_species_id}-${item.pet_quality_id}-${item.pet_level}-${
        AuctionVariantKeyUtility.canonicalBonusKey(item.bonus_lists)
    }-${
        AuctionVariantKeyUtility.canonicalModifierKey(item.modifiers)
    }"
