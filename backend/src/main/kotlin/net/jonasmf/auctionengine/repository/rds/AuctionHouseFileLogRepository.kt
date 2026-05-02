package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouseFileLog
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

interface AuctionHouseDelayStatsProjection {
    fun getMinDelayMinutes(): Long?

    fun getAvgDelayMinutes(): Long?

    fun getMaxDelayMinutes(): Long?
}

@Repository
interface AuctionHouseFileLogRepository : JpaRepository<AuctionHouseFileLog, Long> {
    fun findByAuctionHouseConnectedIdOrderByLastModifiedDesc(
        connectedId: Int,
        pageable: Pageable,
    ): List<AuctionHouseFileLog>

    fun findByAuctionHouseConnectedIdAndLastModified(
        connectedId: Int,
        lastModified: Instant,
    ): AuctionHouseFileLog?

    @Query(
        value =
            """
            SELECT
                CAST(ROUND(MIN(NULLIF(log.time_since_previous_dump, 0)) / 60000.0) AS SIGNED) AS minDelayMinutes,
                CAST(ROUND(AVG(NULLIF(log.time_since_previous_dump, 0)) / 60000.0) AS SIGNED) AS avgDelayMinutes,
                CAST(ROUND(MAX(NULLIF(log.time_since_previous_dump, 0)) / 60000.0) AS SIGNED) AS maxDelayMinutes
            FROM auction_house_file_log log
            JOIN auction_house house ON house.id = log.auction_house_id
            WHERE house.connected_id = :connectedId
              AND log.last_modified BETWEEN DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 72 HOUR) AND UTC_TIMESTAMP(6)
            """,
        nativeQuery = true,
    )
    fun findDelayStatsByConnectedId(
        @Param("connectedId") connectedId: Int,
    ): AuctionHouseDelayStatsProjection
}
