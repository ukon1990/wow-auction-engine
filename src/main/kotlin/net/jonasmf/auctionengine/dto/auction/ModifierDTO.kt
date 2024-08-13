package net.jonasmf.auctionengine.dto.auction

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionItemModifier

data class ModifierDTO( // TODO: Fix? or is it ok?
    val type: String,
    val value: Int
) {
    fun toDBO(): AuctionItemModifier {
        return AuctionItemModifier(
            type = type,
            value = value
        )
    }
}
