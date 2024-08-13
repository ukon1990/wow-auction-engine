package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.*
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import java.io.Serializable
import java.time.LocalDate

@Embeddable
data class AuctionStatsId(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connectedRealmId", insertable = false, updatable = false)
    val connectedRealm: ConnectedRealm,

    @Column(name = "ahTypeId")
    val gameBuildVersion: GameBuildVersion,

    @Column(name = "itemId")
    val itemId: Int = 0,

    @Column(name = "date")
    val date: LocalDate,

    @Column(name = "petSpeciesId")
    val petSpeciesId: Int? = null
) : Serializable