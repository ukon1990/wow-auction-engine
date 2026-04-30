package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionHousePrice
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionHousePriceId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AuctionHousePriceRepository : JpaRepository<AuctionHousePrice, AuctionHousePriceId> {
    fun findAllByConnectedRealmIdAndItemIdOrderByTimestampAsc(
        connectedRealmId: Int,
        itemId: Int,
    ): List<AuctionHousePrice>

    fun findAllByConnectedRealmIdAndItemIdAndModifierKeyOrderByTimestampAsc(
        connectedRealmId: Int,
        itemId: Int,
        modifierKey: String,
    ): List<AuctionHousePrice>

    fun findAllByConnectedRealmIdAndItemIdAndModifierKeyAndBonusKeyOrderByTimestampAsc(
        connectedRealmId: Int,
        itemId: Int,
        modifierKey: String,
        bonusKey: String,
    ): List<AuctionHousePrice>

    fun findAllByTimestampBetweenOrderByTimestampAsc(
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<AuctionHousePrice>
}
