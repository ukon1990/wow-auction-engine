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
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.FileReference
import java.time.ZonedDateTime

@Entity
class ConnectedRealm(
    @Id
    var id: Int,
    @OneToOne(cascade = [CascadeType.PERSIST])
    var auctionHouse: AuctionHouse,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var realms: List<Realm> = emptyList(),
)

@Entity
class AuctionHouseFileLog(
    @Id
    @GeneratedValue
    var id: Long? = null,
    var timestamp: ZonedDateTime,
    @ManyToOne
    var file: FileReference,
)

@Entity
class AuctionHouse(
    @Id
    @GeneratedValue
    var id: Int? = null,
    var lastModified: ZonedDateTime?,
    @Nullable
    var lastRequested: ZonedDateTime?,
    @Nullable
    var nextUpdate: ZonedDateTime,
    @Nullable
    var lowestDelay: Long,
    @Nullable
    var averageDelay: Long = 60,
    @Nullable
    var highestDelay: Long,
    @ManyToOne
    @Nullable
    var tsmFile: FileReference?,
    @ManyToOne
    @Nullable
    var statsFile: FileReference?,
    @ManyToOne
    @Nullable
    var auctionFile: FileReference?,
    var failedAttempts: Int? = 0,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var updateLog: MutableList<AuctionHouseFileLog> = mutableListOf(),
)

@Entity(name = "region")
class RegionDBO(
    @Id
    var id: Int? = null,
    var name: String,
    var type: Region,
)

@Entity
class Realm(
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
