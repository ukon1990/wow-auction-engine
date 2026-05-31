package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import java.time.Instant
import java.time.OffsetDateTime

@Entity
@Table(
    name = "auction_price",
    indexes = [
        Index(name = "idx_auction_item_item_id", columnList = "item_id, id"),
    ],
)
data class AuctionPrice(
    @Id
    var id: Long,
    var buyout: Long?,
    var bid: Long?,
    var quantity: Int,
    var lastModified: Instant? = null,
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    lateinit var auction: Auction
}

@Entity
@Table(
    name = "auction",
    indexes = [
        Index(
            name = "idx_auction_connected_realm_update_deleted",
            columnList = "connected_realm_id, update_history_id, deleted_at",
        ),
        Index(
            name = "idx_auction_item_realm_deleted_last_seen",
            columnList = "item_id, connected_realm_id, deleted_at, last_seen",
        ),
        Index(name = "idx_auction_deleted_at", columnList = "deleted_at"),
    ],
)
class Auction(
    @Id
    var id: String?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_realm_id", nullable = false)
    var connectedRealm: ConnectedRealm,
    var itemId: Int,
    var petSpeciesId: Int? = null,
    var petQualityId: Int? = null,
    var modifierKey: String? = null,
    var bonusKey: String? = null,
    var petLevel: Byte? = null,
    var buyout: Long?,
    var bid: Long?,
    var p25: Long?,
    var p75: Long?,
    var quantity: Int,
    @OneToMany(
        mappedBy = "auction",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var prices: MutableList<AuctionPrice>,
    @Column(name = "first_seen", columnDefinition = "DATETIME(6)")
    var firstSeen: OffsetDateTime?,
    @Column(name = "last_seen", columnDefinition = "DATETIME(6)")
    var lastSeen: OffsetDateTime?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "update_history_id")
    var updateHistory: ConnectedRealmUpdateHistory,
)
