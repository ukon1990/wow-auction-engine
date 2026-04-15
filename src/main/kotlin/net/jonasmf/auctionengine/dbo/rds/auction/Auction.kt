package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import java.time.OffsetDateTime

@Embeddable
data class AuctionId(
    @Column(name = "id")
    val id: Long = 0,
    @Column(name = "connected_realm_id")
    val connectedRealmId: Int = 0,
)

@Entity
@Table(
    name = "auction_item_modifier",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_auction_item_modifier_type_value",
            columnNames = ["type", "value"],
        ),
    ],
)
data class AuctionItemModifier(
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    val id: Long? = null,
    @Column(nullable = false)
    val type: String,
    @Column(nullable = false)
    val value: Int,
)

@Entity
@Table(
    name = "auction_item",
    indexes = [
        Index(name = "idx_auction_item_item_id", columnList = "item_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_auction_item_variant_hash",
            columnNames = ["variant_hash"],
        ),
    ],
)
data class AuctionItem(
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    val id: Long? = null,
    @Column(name = "item_id", nullable = false)
    val itemId: Int,
    @Column(name = "variant_hash", nullable = false, length = 64)
    val variantHash: String,
    @ManyToMany(fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "auction_item_modifier_link",
        joinColumns = [JoinColumn(name = "auction_item_id")],
        inverseJoinColumns = [JoinColumn(name = "modifier_id")],
    )
    @OrderColumn(name = "sort_order")
    val modifiers: MutableList<AuctionItemModifier> = mutableListOf(),
    @Column(name = "bonus_lists", nullable = false)
    val bonusLists: String = "",
    val context: Int? = null,
    @Column(name = "pet_breed_id")
    val petBreedId: Int? = null,
    @Column(name = "pet_level")
    val petLevel: Int? = null,
    @Column(name = "pet_quality_id")
    val petQualityId: Int? = null,
    @Column(name = "pet_species_id")
    val petSpeciesId: Int? = null,
)

@Entity
@Table(
    name = "auction",
    indexes = [
        Index(
            name = "idx_auction_connected_realm_update_deleted",
            columnList = "connected_realm_id, update_history_id, deleted_at",
        ),
        Index(name = "idx_auction_deleted_at", columnList = "deleted_at"),
    ],
)
data class Auction(
    @EmbeddedId
    val id: AuctionId,
    @MapsId("connectedRealmId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_realm_id", nullable = false)
    val connectedRealm: ConnectedRealm,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", nullable = false)
    val item: AuctionItem,
    val quantity: Long,
    val bid: Long?,
    @Column(name = "unit_price")
    val unitPrice: Long?,
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "time_left", nullable = false)
    val timeLeft: AuctionTimeLeft,
    val buyout: Long?,
    @Column(name = "first_seen", columnDefinition = "DATETIME(6)")
    val firstSeen: OffsetDateTime?,
    @Column(name = "last_seen", columnDefinition = "DATETIME(6)")
    val lastSeen: OffsetDateTime?,
    @Column(name = "deleted_at", columnDefinition = "DATETIME(6)")
    val deletedAt: OffsetDateTime? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "update_history_id")
    val updateHistory: ConnectedRealmUpdateHistory,
)
