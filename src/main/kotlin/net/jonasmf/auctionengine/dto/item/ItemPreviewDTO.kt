package net.jonasmf.auctionengine.dto.item

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.dto.MediaDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemPreviewDTO(
    val item: ItemPreviewItemDTO? = null,
    val context: Int? = null,
    @JsonProperty("bonus_list")
    val bonusList: List<Int> = emptyList(),
    val quality: ItemQualityDTO? = null,
    val name: LocaleDTO? = null,
    val media: MediaDTO? = null,
    @JsonProperty("item_class")
    val itemClass: ItemClassReferenceDTO? = null,
    @JsonProperty("item_subclass")
    val itemSubclass: ItemSubclassReferenceDTO? = null,
    @JsonProperty("inventory_type")
    val inventoryType: InventoryTypeDTO? = null,
    val binding: ItemBindingDTO? = null,
)
