package net.jonasmf.auctionengine.dto.auction

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
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
)
