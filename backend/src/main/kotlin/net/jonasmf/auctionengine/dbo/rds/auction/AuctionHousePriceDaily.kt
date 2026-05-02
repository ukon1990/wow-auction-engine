package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.Subselect
import org.hibernate.annotations.Synchronize
import java.time.LocalDate

@Entity
@Immutable
@IdClass(AuctionHousePriceDailyId::class)
@Subselect("SELECT * FROM v_auction_house_daily_prices")
@Synchronize("auction_stats_daily")
class AuctionHousePriceDaily(
    @Id
    @Column(name = "connected_realm_id")
    var connectedRealmId: Int = 0,
    @Id
    @Column(name = "date")
    var date: LocalDate? = null,
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
    @Column(name = "min_price")
    var minPrice: Long = 0,
    @Column(name = "avg_price")
    var avgPrice: Double = 0.0,
    @Column(name = "median_price_25")
    var medianPrice25: Long = 0,
    @Column(name = "median_price_75")
    var medianPrice75: Long = 0,
    @Column(name = "max_price")
    var maxPrice: Long = 0,
    @Column(name = "min_quantity")
    var minQuantity: Long = 0,
    @Column(name = "avg_quantity")
    var avgQuantity: Double = 0.0,
    @Column(name = "max_quantity")
    var maxQuantity: Long = 0,
)
