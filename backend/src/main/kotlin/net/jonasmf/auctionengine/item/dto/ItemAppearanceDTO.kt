package net.jonasmf.auctionengine.item.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.integration.blizzard.dto.Links
import net.jonasmf.auctionengine.integration.blizzard.dto.MediaDTO
import net.jonasmf.auctionengine.integration.blizzard.dto.ReferenceDTO
import net.jonasmf.auctionengine.item.dto.InventoryTypeDTO
import net.jonasmf.auctionengine.item.dto.ItemClassReferenceDTO
import net.jonasmf.auctionengine.item.dto.ItemSubclassReferenceDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemAppearanceDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val slot: InventoryTypeDTO,
    @JsonProperty("item_class")
    val itemClass: ItemClassReferenceDTO,
    @JsonProperty("item_subclass")
    val itemSubclass: ItemSubclassReferenceDTO,
    @JsonProperty("item_display_info_id")
    val itemDisplayInfoId: Int,
    val items: List<ReferenceDTO> = emptyList(),
    val media: MediaDTO,
)
