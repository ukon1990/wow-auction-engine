package net.jonasmf.auctionengine.dto.auction

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.jonasmf.auctionengine.constant.AuctionTimeLeft

@JsonIgnoreProperties(ignoreUnknown = true)
data class AuctionDTO(
    val id: Long,
    val item: AuctionItemDTO,
    val quantity: Long,
    val bid: Long? = null,
    val unit_price: Long?, // Commodity price
    val buyout: Long?, // Realm price
    val time_left: AuctionTimeLeft,
)
