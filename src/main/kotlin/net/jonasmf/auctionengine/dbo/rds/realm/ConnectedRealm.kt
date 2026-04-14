package net.jonasmf.auctionengine.dbo.rds.realm

import jakarta.annotation.Nullable
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.OrderBy
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.FileReference
import java.time.Instant

@Entity
class ConnectedRealm(
    @Id
    var id: Int,
    @OneToOne(cascade = [CascadeType.PERSIST])
    var auctionHouse: AuctionHouse,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var realms: MutableList<Realm> = mutableListOf(),
)

@Entity
class AuctionHouseFileLog(
    @Id
    @GeneratedValue
    var id: Long? = null,
    var lastModified: Instant? = null,
    var timeSincePreviousDump: Long = 0L,
    @ManyToOne(cascade = [CascadeType.ALL], optional = false)
    var file: FileReference = FileReference(),
    @ManyToOne(optional = false)
    @JoinColumn(name = "auction_house_id")
    var auctionHouse: AuctionHouse? = null,
)

@Entity
class AuctionHouse(
    @Id
    @GeneratedValue
    var id: Int? = null,
    var connectedId: Int = 0,
    @Enumerated(EnumType.STRING)
    var region: Region = Region.Europe,
    var autoUpdate: Boolean = false,
    var lastModified: Instant? = null,
    @Nullable
    var lastRequested: Instant? = null,
    @Nullable
    var nextUpdate: Instant? = null,
    @Nullable
    var lowestDelay: Long = 0,
    @Nullable
    var avgDelay: Long = 60,
    @Nullable
    var highestDelay: Long = 0,
    @Nullable
    var gameBuild: Int = 0,
    @Nullable
    var lastDailyPriceUpdate: Instant? = null,
    @Nullable
    var lastHistoryDeleteEvent: Instant? = null,
    @Nullable
    var lastHistoryDeleteEventDaily: Instant? = null,
    @Nullable
    var lastStatsInsert: Instant? = null,
    @Nullable
    var lastTrendUpdateInitiation: Instant? = null,
    @Nullable
    var realmSlugs: String = "",
    @Nullable
    var statsLastModified: Long = 0L,
    @Nullable
    var updateAttempts: Int = 0,
    @ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @Nullable
    var tsmFile: FileReference? = null,
    @ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @Nullable
    var statsFile: FileReference? = null,
    @ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @Nullable
    var auctionFile: FileReference? = null,
    @Lob
    @Nullable
    var realmsJson: String? = null,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, mappedBy = "auctionHouse", orphanRemoval = true)
    @OrderBy("lastModified DESC")
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
