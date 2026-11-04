package net.jonasmf.auctionengine.auction.repository

import net.jonasmf.auctionengine.dbo.rds.auction.ItemPriceDaily
import net.jonasmf.auctionengine.dbo.rds.auction.ItemPriceDailyId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ItemPriceDailyRepository : JpaRepository<ItemPriceDaily, ItemPriceDailyId> {
    fun findAllByConnectedRealmIdAndItemId(
        connectedRealmId: Int,
        itemId: Int,
    ): List<ItemPriceDaily>

    fun findAllByConnectedRealmIdAndItemIdAndPetSpeciesIdAndModifierKeyAndBonusKey(
        connectedRealmId: Int,
        itemId: Int,
        petSpeciesId: Int,
        modifierKey: String,
        bonusKey: String,
    ): List<ItemPriceDaily>
}
