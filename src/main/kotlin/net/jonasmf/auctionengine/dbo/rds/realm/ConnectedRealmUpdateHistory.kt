package net.jonasmf.auctionengine.dbo.rds.realm

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.time.ZonedDateTime

@Entity
@Table(name = "connected_realm_update_history")
data class ConnectedRealmUpdateHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val auctionCount: Int,
    var isActive: Boolean,
    val lastModified: ZonedDateTime,
    val updateTimestamp: ZonedDateTime,
    @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.MERGE])
    @JoinColumn(name = "connected_realm_id")
    val connectedRealm: ConnectedRealm,
)
