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
    val id: String,
    val auctionId: Long,
    val itemId: Int,
    var petSpeciesId: Int? = null,
    var petQualityId: Int? = null,
    var petLevel: Byte? = null,
    var modifierKey: String? = null,
    var bonusList: String? = null,
    var buyout: Long?,
    var bid: Long?,
    var quantity: Int,
)

fun AuctionDTO.toFlatObject(connectedRealmId: Int) =
    FlatAuction(
        id =
            AuctionVariantKeyUtility.variantHash(
                connectedRealmId,
                item.id,
                AuctionVariantKeyUtility.canonicalBonusKey(item.bonusLists),
                AuctionVariantKeyUtility.canonicalModifierKey(item.modifiers),
                item.context,
                item.petBreedId,
                item.petLevel,
                item.petQualityId,
                item.petSpeciesId,
            ),
        auctionId = id,
        itemId = item.id,
        petSpeciesId = item.petSpeciesId,
        petQualityId = item.petQualityId,
        petLevel = item.petLevel,
        modifierKey = AuctionVariantKeyUtility.canonicalModifierKey(item.modifiers),
        bonusList = AuctionVariantKeyUtility.canonicalBonusKey(item.bonusLists),
        buyout = buyout ?: unit_price,
        bid = bid,
        quantity = quantity,
    )

fun FlatAuction.toDBO(
    connectedRealm: ConnectedRealm,
    updateHistory: ConnectedRealmUpdateHistory,
): Auction =
    Auction(
        id = "${connectedRealm.id}-$id",
        connectedRealm = connectedRealm,
        itemId = itemId,
        petSpeciesId = petSpeciesId,
        petQualityId = petQualityId,
        petLevel = petLevel,
        modifierKey = modifierKey,
        bonusKey = bonusList,
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

/**
 * Generates a unique id for this auction
 */
fun AuctionDTO.getUniqueId(): String =
    "${item.id}-${item.petSpeciesId}-${item.petQualityId}-${item.petLevel}-${
        AuctionVariantKeyUtility.canonicalBonusKey(item.bonusLists)
    }-${
        AuctionVariantKeyUtility.canonicalModifierKey(item.modifiers)
    }"
