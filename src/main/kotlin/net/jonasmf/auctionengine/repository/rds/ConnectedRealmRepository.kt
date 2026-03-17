package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ConnectedRealmRepository : JpaRepository<ConnectedRealm, Int> {
    @Query(
        """
        select distinct connectedRealm
        from ConnectedRealm connectedRealm
        join connectedRealm.realms realm
        where upper(realm.region.name) = upper(:#{#region.code})
        """,
    )
    fun findAllByRegion(
        @Param("region") region: Region,
    ): List<ConnectedRealm>
}
