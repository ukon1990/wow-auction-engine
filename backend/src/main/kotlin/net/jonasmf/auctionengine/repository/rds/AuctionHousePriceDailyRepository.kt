package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionHousePriceDaily
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionHousePriceDailyId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuctionHousePriceDailyRepository : JpaRepository<AuctionHousePriceDaily, AuctionHousePriceDailyId> {
    fun findAllByConnectedRealmIdAndItemId(
        connectedRealmId: Int,
        itemId: Int,
    ): List<AuctionHousePriceDaily>

    fun findAllByConnectedRealmIdAndItemIdAndPetSpeciesIdAndModifierKeyAndBonusKey(
        connectedRealmId: Int,
        itemId: Int,
        petSpeciesId: Int,
        modifierKey: String,
        bonusKey: String,
    ): List<AuctionHousePriceDaily>
}
