package net.jonasmf.auctionengine.dto.item

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.dto.LocaleDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemQualityDTO(
    val type: String = "",
    val name: LocaleDTO = LocaleDTO(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemClassReferenceDTO(
    val id: Int,
    val key: Href,
    val name: LocaleDTO,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemSubclassReferenceDTO(
    val id: Int,
    val name: LocaleDTO,
    val key: Href,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InventoryTypeDTO(
    val type: String = "",
    val name: LocaleDTO = LocaleDTO(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemBindingDTO(
    val type: String = "",
    val name: LocaleDTO = LocaleDTO(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemAppearanceReferenceDTO(
    val key: Href,
    val id: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ItemPreviewItemDTO(
    val key: Href,
    val id: Int,
)
