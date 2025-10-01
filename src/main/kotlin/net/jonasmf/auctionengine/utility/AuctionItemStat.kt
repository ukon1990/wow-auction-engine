package net.jonasmf.auctionengine.utility

import net.jonasmf.auctionengine.dto.auction.AuctionDTO

/**
 * Utility class for processing auction statistics.
 * This is a helper class for in-memory processing of auction data.
 */
class AuctionItemStat(
    val ahId: Int,
    val bonusId: String,
    val auction: AuctionDTO,
    val lastModified: Long,
    hour: String,
    val ahTypeId: Int,
) {
    private val prices = mutableMapOf<String, Long?>()
    private val quantities = mutableMapOf<String, Long>()

    init {
        setPrice(hour, auction.unit_price ?: auction.buyout ?: 0)
        addQuantity(hour, auction.quantity)
    }

    fun getPrice(hour: String): Long = prices[hour] ?: Long.MAX_VALUE

    fun setPrice(
        hour: String,
        price: Long?,
    ) {
        prices[hour] = price
    }

    fun getQuantity(hour: String): Long = quantities[hour] ?: 0

    fun addQuantity(
        hour: String,
        quantity: Long,
    ) {
        quantities[hour] = (quantities[hour] ?: 0) + quantity
    }
}
