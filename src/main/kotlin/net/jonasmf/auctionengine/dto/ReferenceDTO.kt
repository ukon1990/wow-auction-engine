package net.jonasmf.auctionengine.dto

/**
 * Data class for repeating id, name, key types.
 * Used in the "parent" objects, and references the main object of the recipe for example
 */
data class ReferenceDTO(
    val id: Int,
    val name: LocaleDTO = LocaleDTO(),
    val key: Href,
)
