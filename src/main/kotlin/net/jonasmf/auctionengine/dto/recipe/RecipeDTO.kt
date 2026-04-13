package net.jonasmf.auctionengine.dto.recipe

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.dto.MediaDTO
import net.jonasmf.auctionengine.dto.ReferenceDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class RecipeModifiedCraftingSlotDTO(
    @JsonProperty("slot_type")
    val slotType: ReferenceDTO,
    @JsonProperty("display_order")
    val displayOrder: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RecipeDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val name: LocaleDTO,
    val description: LocaleDTO? = null,
    val media: MediaDTO,
    val rank: Int? = null,
    @JsonProperty("modified_crafting_slots")
    val modifiedCraftingSlots: List<RecipeModifiedCraftingSlotDTO> = emptyList(),
)
