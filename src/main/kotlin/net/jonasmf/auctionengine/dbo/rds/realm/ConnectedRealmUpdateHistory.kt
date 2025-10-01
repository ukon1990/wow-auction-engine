package net.jonasmf.auctionengine.dbo.rds.realm

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Entity
@Table(
    name = "connected_realm_update_history",
    indexes = [
        Index(
            "idx_cruh_connected_realm_id_last_modified",
            columnList = "connected_realm_id, lastModified",
            unique = true,
        ),
    ],
)
data class ConnectedRealmUpdateHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val auctionCount: Int,
    @Column(columnDefinition = "DATETIME(6)")
    var lastModified: OffsetDateTime? = null,
    @Column(columnDefinition = "DATETIME(6)")
    var updateTimestamp: OffsetDateTime? = null,
    @Column(columnDefinition = "DATETIME(6)")
    var completedTimestamp: OffsetDateTime? = null,
    @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.MERGE])
    @JoinColumn(name = "connected_realm_id")
    val connectedRealm: ConnectedRealm,
) {
    @PrePersist
    fun prePersist() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        if (updateTimestamp == null) updateTimestamp = now
        if (lastModified == null) lastModified = now
    }

    @PreUpdate
    fun preUpdate() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        updateTimestamp = now
    }
}
