package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.Subselect
import org.hibernate.annotations.Synchronize
import java.time.LocalDateTime

@Entity
@Immutable
@IdClass(AuctionHousePriceId::class)
@Subselect("SELECT * FROM v_auction_house_prices")
@Synchronize("auction_stats_hourly")
class AuctionHousePrice(
    @Id
    @Column(name = "connected_realm_id")
    var connectedRealmId: Int = 0,
    @Id
    @Column(name = "auction_timestamp")
    var auctionTimestamp: LocalDateTime? = null,
    @Id
    @Column(name = "item_id")
    var itemId: Int = 0,
    @Id
    @Column(name = "pet_species_id")
    var petSpeciesId: Int = 0,
    @Id
    @Column(name = "modifier_key")
    var modifierKey: String = "",
    @Id
    @Column(name = "bonus_key")
    var bonusKey: String = "",
    @Column(name = "price")
    var price: Long? = null,
    @Column(name = "quantity")
    var quantity: Long? = null,
)
