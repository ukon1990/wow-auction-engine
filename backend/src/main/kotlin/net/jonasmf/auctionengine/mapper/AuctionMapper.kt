package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.rds.auction.Auction
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionPrice
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionItemDTO
import net.jonasmf.auctionengine.dto.auction.ModifierDTO
import net.jonasmf.auctionengine.repository.rds.AuctionItemModifierLinkUpsertRow
import net.jonasmf.auctionengine.repository.rds.AuctionItemUpsertRow
import net.jonasmf.auctionengine.repository.rds.AuctionModifierUpsertRow
import net.jonasmf.auctionengine.repository.rds.AuctionUpsertRow
import net.jonasmf.auctionengine.utility.AuctionVariantKeyUtility
import java.time.OffsetDateTime

data class AuctionModifierKey(
    val type: String,
    val value: Int,
)

data class AuctionItemVariant(
    val variantHash: String,
    val itemId: Int,
    val bonusKey: String,
    val context: Int?,
    val petBreedId: Int?,
    val petLevel: Int?,
    val petQualityId: Int?,
    val petSpeciesId: Int?,
    val modifiers: List<AuctionModifierKey>,
)

data class SnapshotAuction(
    val auctionId: Long,
    val itemVariant: AuctionItemVariant,
    val quantity: Long,
    val bid: Long?,
    val unitPrice: Long?,
    val buyout: Long?,
    val timeLeftOrdinal: Int,
)

fun ModifierDTO.toAuctionModifierKey(): AuctionModifierKey =
    AuctionModifierKey(
        type = type,
        value = value,
    )

fun AuctionItemDTO.toAuctionItemVariant(): AuctionItemVariant {
    val bonusKey = AuctionVariantKeyUtility.canonicalBonusKey(bonus_lists)
    val modifiers = modifiers.orEmpty().map(ModifierDTO::toAuctionModifierKey)
    val modifierKey = AuctionVariantKeyUtility.canonicalTypedModifierKey(this.modifiers)
    return AuctionItemVariant(
        variantHash =
            AuctionVariantKeyUtility.variantHash(
                itemId = id,
                bonusKey = bonusKey,
                modifierKey = modifierKey,
                context = context,
                petBreedId = pet_breed_id,
                petLevel = pet_level,
                petQualityId = pet_quality_id,
                petSpeciesId = pet_species_id,
            ),
        itemId = id,
        bonusKey = bonusKey,
        context = context,
        petBreedId = pet_breed_id,
        petLevel = pet_level,
        petQualityId = pet_quality_id,
        petSpeciesId = pet_species_id,
        modifiers = modifiers.sortedWith(compareBy(AuctionModifierKey::type, AuctionModifierKey::value)),
    )
}

fun AuctionDTO.toSnapshotAuction(): SnapshotAuction =
    SnapshotAuction(
        auctionId = id,
        itemVariant = item.toAuctionItemVariant(),
        quantity = quantity,
        bid = bid,
        unitPrice = unit_price,
        buyout = buyout,
        timeLeftOrdinal = time_left.ordinal,
    )

fun AuctionModifierKey.toUpsertRow(): AuctionModifierUpsertRow =
    AuctionModifierUpsertRow(
        type = type,
        value = value,
    )

fun AuctionItemVariant.toUpsertRow(): AuctionItemUpsertRow =
    AuctionItemUpsertRow(
        variantHash = variantHash,
        itemId = itemId,
        bonusLists = bonusKey,
        context = context,
        petBreedId = petBreedId,
        petLevel = petLevel,
        petQualityId = petQualityId,
        petSpeciesId = petSpeciesId,
    )

fun SnapshotAuction.toUpsertRow(
    connectedRealmId: Int,
    auctionItemId: Long,
    updateHistoryId: Long,
    snapshotTime: OffsetDateTime,
): AuctionUpsertRow =
    AuctionUpsertRow(
        id = auctionId,
        connectedRealmId = connectedRealmId,
        itemId = auctionItemId,
        quantity = quantity,
        bid = bid,
        unitPrice = unitPrice,
        timeLeft = timeLeftOrdinal,
        buyout = buyout,
        firstSeen = snapshotTime,
        lastSeen = snapshotTime,
        updateHistoryId = updateHistoryId,
    )

fun AuctionItemVariant.toModifierLinkRows(
    auctionItemId: Long,
    modifierIds: Map<AuctionModifierKey, Long>,
): List<AuctionItemModifierLinkUpsertRow> =
    modifiers.mapIndexed { index, modifier ->
        AuctionItemModifierLinkUpsertRow(
            auctionItemId = auctionItemId,
            sortOrder = index,
            modifierId = modifierIds.getValue(modifier),
        )
    }

fun AuctionDTO.toDBO(
    connectedRealm: ConnectedRealm,
    updateHistory: ConnectedRealmUpdateHistory,
): Auction =
    Auction(
        id = null,
        connectedRealm = connectedRealm,
        itemId = item.id,
        petSpeciesId = item.pet_species_id,
        petQualityId = item.pet_quality_id,
        petLevel = item.pet_level,
        prices =
            mutableListOf(
                toAuctionPriceDBO(updateHistory),
            ),
        // TODO: Mapping for this later? item = item.toDBO(),
        quantity = quantity,
        buyout = buyout ?: unit_price,
        bid = bid,
        p25 = null,
        p75 = null,
        firstSeen = null,
        lastSeen = null,
        updateHistory = updateHistory,
    )

fun AuctionDTO.getUniqueId(): String =
    "${item.id}-${item.pet_species_id}-${item.pet_quality_id}-${item.pet_level}-${
        AuctionVariantKeyUtility.canonicalBonusKey(item.bonus_lists)}-${
        AuctionVariantKeyUtility.canonicalModifierKey(item.modifiers)
    }"

fun AuctionDTO.toAuctionPriceDBO(updateHistory: ConnectedRealmUpdateHistory): AuctionPrice =
    AuctionPrice(
        id = id,
        buyout = buyout ?: unit_price,
        bid = bid,
        quantity = quantity,
        lastModified = updateHistory.lastModified?.toInstant(),
    )
