package net.jonasmf.auctionengine.dto.modifiedcrafting

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.dto.ReferenceDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModifiedCraftingCategoryDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val name: LocaleDTO,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ReagentSlotTypeDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val description: LocaleDTO,
    @JsonProperty("compatible_categories")
    val compatibleCategories: List<ReferenceDTO> = emptyList(),
)
