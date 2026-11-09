package net.jonasmf.auctionengine.integration.blizzard.dto

import net.jonasmf.auctionengine.locale.LocaleDTO

/**
 * Data class for repeating id, name, key types.
 * Used in the "parent" objects, and references the main object of the recipe for example
 */
data class ReferenceDTO(
    val id: Int,
    val name: LocaleDTO? = null,
    val key: Href,
) {
    fun resolvedName(): LocaleDTO = name ?: LocaleDTO()
}
