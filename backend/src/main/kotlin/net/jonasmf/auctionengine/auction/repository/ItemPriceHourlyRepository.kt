package net.jonasmf.auctionengine.auction.repository

import net.jonasmf.auctionengine.dbo.rds.auction.ItemPriceHourly
import net.jonasmf.auctionengine.dbo.rds.auction.ItemPriceHourlyId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ItemPriceHourlyRepository : JpaRepository<ItemPriceHourly, ItemPriceHourlyId> {
    fun findAllByConnectedRealmIdAndItemIdOrderByTimestampAsc(
        connectedRealmId: Int,
        itemId: Int,
    ): List<ItemPriceHourly>

    fun findAllByConnectedRealmIdAndItemIdAndModifierKeyOrderByTimestampAsc(
        connectedRealmId: Int,
        itemId: Int,
        modifierKey: String,
    ): List<ItemPriceHourly>

    fun findAllByConnectedRealmIdAndItemIdAndModifierKeyAndBonusKeyOrderByTimestampAsc(
        connectedRealmId: Int,
        itemId: Int,
        modifierKey: String,
        bonusKey: String,
    ): List<ItemPriceHourly>

    fun findAllByTimestampBetweenOrderByTimestampAsc(
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<ItemPriceHourly>
}
