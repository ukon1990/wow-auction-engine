package net.jonasmf.auctionengine.dbo.rds.realm

import jakarta.annotation.Nullable
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.dbo.rds.FileReference
import java.time.ZonedDateTime

@Entity
data class ConnectedRealm(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.PERSIST])
    val auctionHouse: AuctionHouse,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    val realms: List<Realm> = emptyList(),
)

@Entity
data class AuctionHouseFileLog(
    @Id
    @GeneratedValue
    val id: Long? = null,
    val timestamp: ZonedDateTime,
    @ManyToOne
    val file: FileReference,
)

@Entity
data class AuctionHouse(
    @Id
    @GeneratedValue
    val id: Int? = null,
    var lastModified: ZonedDateTime?,
    @Nullable
    val lastRequested: ZonedDateTime?,
    @Nullable
    var nextUpdate: ZonedDateTime,
    @Nullable
    val lowestDelay: Long,
    @Nullable
    var averageDelay: Long = 60,
    @Nullable
    val highestDelay: Long,
    @ManyToOne
    @Nullable
    val tsmFile: FileReference?,
    @ManyToOne
    @Nullable
    val statsFile: FileReference?,
    @ManyToOne
    @Nullable
    val auctionFile: FileReference?,
    val failedAttempts: Int? = 0,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    val updateLog: List<AuctionHouseFileLog> = emptyList(),
)

@Entity(name = "region")
data class RegionDBO(
    @Id
    val id: Int? = null,
    val name: String,
)

@Entity
data class Realm(
    @Id
    val id: Int,
    @ManyToOne(cascade = [CascadeType.REFRESH])
    val region: RegionDBO,
    val name: String,
    val category: String,
    val locale: Locale,
    val timezone: String,
    val gameBuild: GameBuildVersion,
    val slug: String,
)
