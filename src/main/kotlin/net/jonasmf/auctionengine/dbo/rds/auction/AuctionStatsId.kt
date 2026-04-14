package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import net.jonasmf.auctionengine.constant.GameBuildVersion
import java.io.Serializable
import java.time.LocalDate

@Embeddable
data class AuctionStatsId(
    @Column(name = "connected_realm_id")
    val connectedRealmId: Int,
    @Column(name = "ah_type_id")
    val gameBuildVersion: GameBuildVersion,
    @Column(name = "item_id")
    val itemId: Int = 0,
    @Column(name = "date")
    val date: LocalDate,
    @Column(name = "pet_species_id")
    val petSpeciesId: Int? = null,
    @Column(name = "modifier_key")
    val modifierKey: String = "",
    @Column(name = "bonus_key")
    val bonusKey: String = "",
) : Serializable {
    override fun toString(): String =
        "$connectedRealmId-$gameBuildVersion-$itemId-$date-${petSpeciesId ?: ""}-$modifierKey-$bonusKey"
}
