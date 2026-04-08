package net.jonasmf.auctionengine.dto.auction

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.dbo.rds.auction.Auction
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionId
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory

@JsonIgnoreProperties(ignoreUnknown = true)
data class AuctionDTO(
    val id: Long,
    val item: AuctionItemDTO,
    val quantity: Long,
    val bid: Long? = null,
    val unit_price: Long?, // Commodity price
    val buyout: Long?, // Realm price
    val time_left: AuctionTimeLeft,
) {
    fun toDBO(
        connectedRealm: ConnectedRealm,
        updateHistory: ConnectedRealmUpdateHistory,
    ): Auction =
        Auction(
            id =
                AuctionId(
                    id = id,
                    connectedRealm = connectedRealm,
                ),
            item = item.toDBO(),
            quantity = quantity,
            bid = bid,
            unitPrice = unit_price,
            buyout = buyout,
            timeLeft = time_left,
            firstSeen = null,
            lastSeen = null,
            updateHistory = updateHistory,
        )
}
