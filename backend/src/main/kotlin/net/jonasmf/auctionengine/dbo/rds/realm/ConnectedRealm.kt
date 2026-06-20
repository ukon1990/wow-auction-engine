package net.jonasmf.auctionengine.dbo.rds.realm

import jakarta.annotation.Nullable
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
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
    var lastHistoryDeleteEventDaily: Instant? = null, // TODO: Probably redundant due to SQL query?
    @Nullable
    var updateAttempts: Int = 0,
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
    var id: Int,
    @ManyToOne(cascade = [CascadeType.REFRESH])
    var region: RegionDBO,
    var name: String,
    var category: String,
    var locale: Locale,
    var timezone: String,
    var gameBuild: GameBuildVersion,
    var slug: String,
)
