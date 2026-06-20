package net.jonasmf.auctionengine.dto.auction

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class AuctionItemDTO(
    val id: Int,
    val modifiers: List<ModifierDTO>? = null,
    @JsonProperty("bonus_lists")
    val bonusLists: List<Int>? = null,
    val context: Int? = null, // Raid, Dungeon, Delve, PvP, etc.
    // Pet specific fields
    @JsonProperty("pet_breed_id")
    val petBreedId: Int? = null,
    @JsonProperty("pet_level")
    val petLevel: Byte? = null,
    @JsonProperty("pet_quality_id")
    val petQualityId: Int? = null,
    @JsonProperty("pet_species_id")
    val petSpeciesId: Int? = null,
)
