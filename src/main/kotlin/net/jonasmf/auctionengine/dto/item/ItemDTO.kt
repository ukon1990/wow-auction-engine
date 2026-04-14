package net.jonasmf.auctionengine.dto.item

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.dto.MediaDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val name: LocaleDTO,
    val quality: ItemQualityDTO,
    val level: Int = 0,
    @JsonProperty("required_level")
    val requiredLevel: Int = 0,
    val media: MediaDTO,
    @JsonProperty("item_class")
    val itemClass: ItemClassReferenceDTO,
    @JsonProperty("item_subclass")
    val itemSubclass: ItemSubclassReferenceDTO,
    @JsonProperty("inventory_type")
    val inventoryType: InventoryTypeDTO,
    val binding: ItemBindingDTO? = null,
    @JsonProperty("purchase_price")
    val purchasePrice: Int = 0,
    @JsonProperty("sell_price")
    val sellPrice: Int = 0,
    @JsonProperty("max_count")
    val maxCount: Int = 0,
    @JsonProperty("is_equippable")
    val isEquippable: Boolean = false,
    @JsonProperty("is_stackable")
    val isStackable: Boolean = false,
    @JsonProperty("preview_item")
    val previewItem: ItemPreviewDTO? = null,
    @JsonProperty("purchase_quantity")
    val purchaseQuantity: Int = 0,
    val appearances: List<ItemAppearanceReferenceDTO> = emptyList(),
)
