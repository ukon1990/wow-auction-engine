package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
interface ConnectedRealmUpdateHistoryRepository : JpaRepository<ConnectedRealmUpdateHistory, Long> {
    fun findByConnectedRealmIdAndIsActive(
        connectedRealmId: Int,
        isActive: Boolean,
    ): ConnectedRealmUpdateHistory?

    @Modifying
    @Query(
        """
            update ConnectedRealmUpdateHistory h
            set h.isActive = false
            where h.connectedRealm.id = :connectedRealmId and h.isActive = true
        """,
    )
    fun deactivateActive(
        @Param("connectedRealmId") connectedRealmId: Int,
    ): Int

    @Query(
        """
        select h from ConnectedRealmUpdateHistory h 
        where h.connectedRealm.id = :connectedRealmId 
        and h.lastModified = :updateTimestamp
    """,
    )
    fun findByConnectedRealmIdAndUpdateTimestamp(
        @Param("connectedRealmId") connectedRealmId: Int,
        @Param("updateTimestamp") updateTimestamp: ZonedDateTime,
    ): ConnectedRealmUpdateHistory?
}
