package net.jonasmf.auctionengine.profession.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.integration.blizzard.dto.Links
import net.jonasmf.auctionengine.locale.LocaleDTO
import net.jonasmf.auctionengine.integration.blizzard.dto.ReferenceDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModifiedCraftingCategoryDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val name: LocaleDTO? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ReagentSlotTypeDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val description: LocaleDTO? = null,
    @JsonProperty("compatible_categories")
    val compatibleCategories: List<ReferenceDTO> = emptyList(),
)
