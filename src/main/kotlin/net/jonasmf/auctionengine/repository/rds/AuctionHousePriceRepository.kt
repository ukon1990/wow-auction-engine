package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionHousePrice
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionHousePriceId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AuctionHousePriceRepository : JpaRepository<AuctionHousePrice, AuctionHousePriceId> {
    fun findAllByConnectedRealmIdAndItemIdOrderByAuctionTimestampAsc(
        connectedRealmId: Int,
        itemId: Int,
    ): List<AuctionHousePrice>

    fun findAllByAuctionTimestampBetweenOrderByAuctionTimestampAsc(
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<AuctionHousePrice>
}
