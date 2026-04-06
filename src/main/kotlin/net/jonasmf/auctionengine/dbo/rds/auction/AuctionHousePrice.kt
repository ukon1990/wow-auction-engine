package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.Subselect
import org.hibernate.annotations.Synchronize
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Immutable
@IdClass(AuctionHousePriceId::class)
@Subselect("SELECT * FROM v_auction_house_prices")
@Synchronize("hourly_auction_stats")
class AuctionHousePrice(
    @Id
    @Column(name = "connected_realm_id")
    var connectedRealmId: Int = 0,
    @Id
    @Column(name = "date")
    var date: LocalDate? = null,
    @Id
    @Column(name = "ah_type_id")
    var ahTypeId: Int = 0,
    @Id
    @Column(name = "item_id")
    var itemId: Int = 0,
    @Id
    @Column(name = "pet_species_id")
    var petSpeciesId: Int = 0,
    @Id
    @Column(name = "hour_of_day")
    var hourOfDay: Int = 0,
    @Column(name = "auction_timestamp")
    var auctionTimestamp: LocalDateTime? = null,
    @Column(name = "price")
    var price: Long? = null,
    @Column(name = "quantity")
    var quantity: Long? = null,
)
