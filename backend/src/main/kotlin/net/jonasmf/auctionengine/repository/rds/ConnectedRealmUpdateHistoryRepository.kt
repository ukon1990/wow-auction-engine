package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

interface AuctionHouseDelayStatsProjection {
    fun getMinDelayMinutes(): Long?

    fun getAvgDelayMinutes(): Long?

    fun getMaxDelayMinutes(): Long?
}

@Repository
interface ConnectedRealmUpdateHistoryRepository : JpaRepository<ConnectedRealmUpdateHistory, Long> {
    @Query(
        """
        select h from ConnectedRealmUpdateHistory h
        where h.connectedRealm.id = :connectedRealmId
        and h.lastModified = :updateTimestamp
    """,
    )
    fun findByConnectedRealmIdAndUpdateTimestamp(
        @Param("connectedRealmId") connectedRealmId: Int,
        @Param("updateTimestamp") updateTimestamp: OffsetDateTime,
    ): ConnectedRealmUpdateHistory?

    @Modifying
    @Query(
        """
        update ConnectedRealmUpdateHistory h
        set h.completedTimestamp = now()
        where h.id = :id
    """,
    )
    fun updateCompletedTimeById(
        @Param("id") id: Long,
    ): Int

    @Query(
        value =
            """
            SELECT
                CAST(ROUND(MIN(delay_minutes)) AS SIGNED) AS minDelayMinutes,
                CAST(ROUND(AVG(delay_minutes)) AS SIGNED) AS avgDelayMinutes,
                CAST(ROUND(MAX(delay_minutes)) AS SIGNED) AS maxDelayMinutes
            FROM (
                SELECT
                    last_modified,
                    TIMESTAMPDIFF(
                        MICROSECOND,
                        LAG(last_modified) OVER (
                            PARTITION BY connected_realm_id
                            ORDER BY last_modified
                        ),
                        last_modified
                    ) / 60000000.0 AS delay_minutes
                FROM connected_realm_update_history
                WHERE connected_realm_id = :connectedId
                  AND last_modified IS NOT NULL
            ) history_intervals
            WHERE last_modified BETWEEN DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 72 HOUR) AND UTC_TIMESTAMP(6)
              AND delay_minutes > 0
            """,
        nativeQuery = true,
    )
    fun findDelayStatsByConnectedId(
        @Param("connectedId") connectedId: Int,
    ): AuctionHouseDelayStatsProjection
}
