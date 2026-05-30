package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

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
}
