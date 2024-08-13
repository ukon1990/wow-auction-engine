package net.jonasmf.auctionengine.dbo.rds.auction

import jakarta.persistence.*
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import org.springframework.beans.factory.annotation.Value
import java.time.ZonedDateTime

@Embeddable
data class AuctionId(
    val id: Long,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connectedRealmId", insertable = false, updatable = false)
    val connectedRealm: ConnectedRealm,
)

@Entity
data class AuctionItemModifier(
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    val id: Long? = null,
    val type: String,
    val value: Int
)

@Entity
data class AuctionItem(
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    val id: Long? = null,
    val itemId: Int,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val modifiers: List<AuctionItemModifier>? = null,
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
)
