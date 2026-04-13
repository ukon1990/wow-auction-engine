package net.jonasmf.auctionengine.dto.modifiedcrafting

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.ReferenceDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModifiedCraftingIndexDTO(
    @JsonProperty("_links")
    val links: Links,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModifiedCraftingCategoryIndexDTO(
    @JsonProperty("_links")
    val links: Links,
    val categories: List<ReferenceDTO> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ReagentSlotTypeIndexDTO(
    @JsonProperty("_links")
    val links: Links,
    @JsonProperty("slot_types")
    val slotTypes: List<ReferenceDTO> = emptyList(),
)
