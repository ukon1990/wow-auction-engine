package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.auction.Auction
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Repository
interface AuctionRepository : JpaRepository<Auction, AuctionId> {
    @Modifying
    @Transactional
    @Query(
        """
        INSERT INTO auction (id, connected_realm_id, item_id, quantity, bid, unit_price, time_left, buyout, first_seen, last_seen, update_history_id)
        VALUES (:id, :connectedRealmId, :itemId, :quantity, :bid, :unitPrice, :timeLeft, :buyout, :firstSeen, :lastSeen, :updateHistoryId)
        ON DUPLICATE KEY UPDATE
            quantity = VALUES(quantity),
            bid = VALUES(bid),
            unit_price = VALUES(unit_price),
            time_left = VALUES(time_left),
            buyout = VALUES(buyout),
            last_seen = VALUES(last_seen),
            update_history_id = VALUES(update_history_id)
    """,
        nativeQuery = true,
    )
    fun upsertAuction(
        @Param("id") id: Long,
        @Param("connectedRealmId") connectedRealmId: Int,
        @Param("itemId") itemId: Long,
        @Param("quantity") quantity: Long,
        @Param("bid") bid: Long?,
        @Param("unitPrice") unitPrice: Long?,
        @Param("timeLeft") timeLeft: Int,
        @Param("buyout") buyout: Long?,
        @Param("firstSeen") firstSeen: ZonedDateTime?,
        @Param("lastSeen") lastSeen: ZonedDateTime?,
        @Param("updateHistoryId") updateHistoryId: Long,
    ): Int
}
