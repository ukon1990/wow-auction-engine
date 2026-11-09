package net.jonasmf.auctionengine.item.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.integration.blizzard.dto.Links
import net.jonasmf.auctionengine.locale.LocaleDTO
import net.jonasmf.auctionengine.integration.blizzard.dto.ReferenceDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemClassDTO(
    @JsonProperty("_links")
    val links: Links,
    @JsonProperty("class_id")
    val classId: Int,
    val name: LocaleDTO,
    @JsonProperty("item_subclasses")
    val itemSubclasses: List<ReferenceDTO> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemSubclassDTO(
    @JsonProperty("_links")
    val links: Links,
    @JsonProperty("class_id")
    val classId: Int,
    @JsonProperty("subclass_id")
    val subclassId: Int,
    @JsonProperty("display_name")
    val displayName: LocaleDTO,
    @JsonProperty("hide_subclass_in_tooltips")
    val hideSubclassInTooltips: Boolean = false,
)
