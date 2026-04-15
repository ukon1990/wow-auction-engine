package net.jonasmf.auctionengine.utility

import net.jonasmf.auctionengine.dto.auction.ModifierDTO
import java.security.MessageDigest

object AuctionVariantKeyUtility {
    fun canonicalBonusKey(bonusLists: List<Int>?): String =
        bonusLists
            .orEmpty()
            .sorted()
            .joinToString(",")

    fun canonicalModifierKey(modifiers: List<ModifierDTO>?): String =
        modifiers
            .orEmpty()
            .sortedBy(ModifierDTO::value)
            .joinToString(",") { it.value.toString() }

    fun canonicalTypedModifierKey(modifiers: List<ModifierDTO>?): String =
        modifiers
            .orEmpty()
            .sortedWith(compareBy(ModifierDTO::type, ModifierDTO::value))
            .joinToString(",") { "${it.type}:${it.value}" }

    fun variantHash(
        itemId: Int,
        bonusKey: String,
        modifierKey: String,
        context: Int?,
        petBreedId: Int?,
        petLevel: Int?,
        petQualityId: Int?,
        petSpeciesId: Int?,
    ): String =
        sha256Hex(
            listOf(
                itemId.toString(),
                bonusKey,
                modifierKey,
                context?.toString() ?: "",
                petBreedId?.toString() ?: "",
                petLevel?.toString() ?: "",
                petQualityId?.toString() ?: "",
                petSpeciesId?.toString() ?: "",
            ).joinToString("|"),
        )

    private fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
