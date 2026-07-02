package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Optional

@Repository
interface AuctionHouseRepository : JpaRepository<AuctionHouse, Int> {
    fun findByConnectedId(connectedId: Int): Optional<AuctionHouse>

    fun findAllByConnectedIdIn(connectedIds: Collection<Int>): List<AuctionHouse>

    fun findAllByRegion(region: Region): List<AuctionHouse>

    fun findAllByRegionAndNextUpdateLessThanEqualOrderByNextUpdateAsc(
        region: Region,
        nextUpdate: Instant,
        pageable: Pageable,
    ): List<AuctionHouse>

    @Modifying
    @Query(
        """
        UPDATE AuctionHouse a
        SET a.lastDailyPriceUpdate = :lastDailyPriceUpdate
        WHERE a.connectedId = :connectedId
        """,
    )
    fun updateLastDailyPriceUpdate(
        @Param("connectedId") connectedId: Int,
        @Param("lastDailyPriceUpdate") lastDailyPriceUpdate: Instant,
    ): Int

    @Modifying
    @Query(
        """
        UPDATE AuctionHouse a
        SET a.lastHistoryDeleteEvent = :lastHistoryDeleteEvent
        WHERE a.connectedId = :connectedRealmId
    """,
    )
    fun updateLastHistoryDeleteEvent(
        connectedRealmId: Int,
        lastHistoryDeleteEvent: Instant,
    ): Int

    @Modifying
    @Query(
        """
        UPDATE AuctionHouse a
        SET a.lastHistoryDeleteEventDaily = :lastHistoryDeleteEventDaily
        WHERE a.connectedId = :connectedRealmId
    """,
    )
    fun updateLastHistoryDeleteEventDaily(
        connectedRealmId: Int,
        lastHistoryDeleteEventDaily: Instant,
    ): Int
}
