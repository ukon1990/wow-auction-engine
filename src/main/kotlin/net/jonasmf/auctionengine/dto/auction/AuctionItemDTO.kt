package net.jonasmf.auctionengine.dto.auction

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionItem
import net.jonasmf.auctionengine.utility.AuctionVariantKeyUtility

data class AuctionItemDTO(
    val id: Int,
    val modifiers: List<ModifierDTO>? = null,
    val bonus_lists: List<Int>? = null,
    val context: Int? = null, // Raid, Dungeon, Delve, PvP, etc.
    // Pet specific fields
    val pet_breed_id: Int? = null,
    val pet_level: Int? = null,
    val pet_quality_id: Int? = null,
    val pet_species_id: Int? = null,
) {
    fun toDBO(): AuctionItem =
        AuctionItem(
            id = null,
            itemId = id,
            modifiers = modifiers?.map { it.toDBO() }?.toMutableList(),
            bonusLists = AuctionVariantKeyUtility.canonicalBonusKey(bonus_lists),
            context = context,
            petBreedId = pet_breed_id,
            petLevel = pet_level,
            petQualityId = pet_quality_id,
            petSpeciesId = pet_species_id,
        )
}
