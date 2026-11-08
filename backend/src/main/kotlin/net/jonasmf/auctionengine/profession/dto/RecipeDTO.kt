package net.jonasmf.auctionengine.profession.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import net.jonasmf.auctionengine.integration.blizzard.dto.Links
import net.jonasmf.auctionengine.locale.LocaleDTO
import net.jonasmf.auctionengine.integration.blizzard.dto.MediaDTO
import net.jonasmf.auctionengine.integration.blizzard.dto.ReferenceDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class RecipeModifiedCraftingSlotDTO(
    @JsonProperty("slot_type")
    val slotType: ReferenceDTO,
    @JsonProperty("display_order")
    val displayOrder: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RecipeReagentDTO(
    val reagent: ReferenceDTO,
    val quantity: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CraftedQuantityDTO(
    val value: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RecipeDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val name: LocaleDTO,
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val description: LocaleDTO? = null,
    val media: MediaDTO,
    @JsonProperty("crafted_item")
    val craftedItem: ReferenceDTO? = null,
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val reagents: List<RecipeReagentDTO> = emptyList(),
    @JsonProperty("crafted_quantity")
    val craftedQuantity: CraftedQuantityDTO? = null,
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val rank: Int? = null,
    @JsonProperty("modified_crafting_slots")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val modifiedCraftingSlots: List<RecipeModifiedCraftingSlotDTO>? = null,
)
