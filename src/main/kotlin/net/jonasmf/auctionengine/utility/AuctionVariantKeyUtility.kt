package net.jonasmf.auctionengine.utility

import net.jonasmf.auctionengine.dto.auction.ModifierDTO

object AuctionVariantKeyUtility {
    fun canonicalBonusKey(bonusLists: List<Int>?): String =
        bonusLists
            .orEmpty()
            .sorted()
            .joinToString(",")

    fun canonicalModifierKey(modifiers: List<ModifierDTO>?): String =
        modifiers
            .orEmpty()
            .sortedBy { it.value }
            .joinToString(",") { it.value.toString() }
}
