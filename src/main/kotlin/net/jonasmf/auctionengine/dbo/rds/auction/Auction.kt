package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import org.springframework.beans.factory.annotation.Value
import java.time.ZonedDateTime

@Embeddable
data class AuctionId(
    @Column(name = "id")
    val id: Long,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_realm_id")
    val connectedRealm: ConnectedRealm,
)

@Entity
data class AuctionItemModifier(
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    val id: Long? = null,
    val type: String,
    val value: Int,
)

@Entity
data class AuctionItem(
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    val id: Long? = null,
    val itemId: Int,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    val modifiers: MutableList<AuctionItemModifier>? = mutableListOf(),
    val context: Int?,
    val petBreedId: Int?,
    val petLevel: Int?,
    val petQualityId: Int?,
    val petSpeciesId: Int?,
)

@Entity
data class Auction(
    @EmbeddedId
    val id: AuctionId,
    @OneToOne(fetch = FetchType.EAGER, cascade = [CascadeType.REMOVE])
    val item: AuctionItem,
    val quantity: Long,
    val unitPrice: Long?,
    val timeLeft: AuctionTimeLeft,
    val buyout: Long?,
    @Value("CURRENT_TIMESTAMP")
    val firstSeen: ZonedDateTime?,
    val lastSeen: ZonedDateTime?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "update_history_id")
    val updateHistory: ConnectedRealmUpdateHistory,
)
