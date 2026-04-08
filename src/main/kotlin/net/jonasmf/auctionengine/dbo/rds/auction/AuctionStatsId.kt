package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
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
    val petSpeciesId: Int? = null,
    @Column(name = "modifier_key")
    val modifierKey: String = "",
    @Column(name = "bonus_key")
    val bonusKey: String = "",
) : Serializable {
    override fun toString(): String =
        "${connectedRealm.id}-$gameBuildVersion-$itemId-$date-${petSpeciesId ?: ""}-$modifierKey-$bonusKey"
}
