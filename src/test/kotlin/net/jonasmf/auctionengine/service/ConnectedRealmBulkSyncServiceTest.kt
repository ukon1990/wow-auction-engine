package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.repository.rds.RegionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository as AuctionHouseEntityRepository

class ConnectedRealmBulkSyncServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var connectedRealmBulkSyncService: ConnectedRealmBulkSyncService

    @Autowired
    lateinit var connectedRealmRepository: ConnectedRealmRepository

    @Autowired
    lateinit var regionRepository: RegionRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var auctionHouseRepository: AuctionHouseEntityRepository

    @Test
    fun `repeated sync preserves auction house id`() {
        seedRegion(Region.Europe)

        connectedRealmBulkSyncService.sync(
            listOf(connectedRealm(id = 101, realmName = "Initial Realm", slug = "initial")),
        )
        val initialAuctionHouseId = auctionHouseIdFor(101)

        connectedRealmBulkSyncService.sync(
            listOf(connectedRealm(id = 101, realmName = "Updated Realm", slug = "updated")),
        )

        assertEquals(initialAuctionHouseId, auctionHouseIdFor(101))
    }

    @Test
    fun `repeated sync does not create extra auction house rows`() {
        seedRegion(Region.Europe)

        connectedRealmBulkSyncService.sync(
            listOf(connectedRealm(id = 201, realmName = "Realm One", slug = "realm-one")),
        )
        connectedRealmBulkSyncService.sync(
            listOf(connectedRealm(id = 201, realmName = "Realm One Changed", slug = "realm-one-changed")),
        )

        assertEquals(1, countRows("auction_house"))
    }

    @Test
    fun `sync refreshes child realm metadata for existing connected realm`() {
        seedRegion(Region.Europe)

        connectedRealmBulkSyncService.sync(
            listOf(
                connectedRealm(
                    id = 301,
                    realmName = "Old Name",
                    slug = "old-name",
                    extraRealmId = 302,
                    extraRealmName = "Second Realm",
                    extraRealmSlug = "second-realm",
                ),
            ),
        )

        connectedRealmBulkSyncService.sync(listOf(connectedRealm(id = 301, realmName = "New Name", slug = "new-name")))

        val saved = connectedRealmRepository.findById(301).orElseThrow()
        assertEquals(1, saved.realms.size)
        assertEquals("New Name", saved.realms.single().name)
        assertEquals("new-name", saved.realms.single().slug)
    }

    @Test
    fun `mixed batch only creates auction houses for new connected realms`() {
        seedRegion(Region.Europe)

        connectedRealmBulkSyncService.sync(
            listOf(connectedRealm(id = 401, realmName = "Existing Realm", slug = "existing-realm")),
        )
        val existingAuctionHouseId = auctionHouseIdFor(401)

        connectedRealmBulkSyncService.sync(
            listOf(
                connectedRealm(id = 401, realmName = "Existing Realm Updated", slug = "existing-realm-updated"),
                connectedRealm(id = 402, realmName = "New Realm", slug = "new-realm"),
            ),
        )

        val newAuctionHouseId = auctionHouseIdFor(402)
        assertEquals(existingAuctionHouseId, auctionHouseIdFor(401))
        assertNotEquals(existingAuctionHouseId, newAuctionHouseId)
        assertEquals(2, countRows("auction_house"))
    }

    @Test
    fun `sync repairs stale connected realm link and normalizes auction house metadata`() {
        seedRegion(Region.Europe)

        connectedRealmBulkSyncService.sync(
            listOf(connectedRealm(id = 501, realmName = "Original Realm", slug = "original-realm")),
        )
        val authoritativeAuctionHouseId = auctionHouseIdFor(501)

        val staleAuctionHouse =
            auctionHouseRepository.saveAndFlush(
                AuctionHouse(
                    connectedId = 0,
                    region = Region.Korea,
                    lastModified = null,
                    lastRequested = null,
                    nextUpdate = null,
                    lowestDelay = 0,
                    avgDelay = 60,
                    highestDelay = 0,
                    tsmFile = null,
                    statsFile = null,
                    auctionFile = null,
                    updateAttempts = 0,
                ),
            )

        execute(
            "UPDATE connected_realm SET auction_house_id = ? WHERE id = ?",
            requireNotNull(staleAuctionHouse.id),
            501,
        )

        val authoritativeAuctionHouse = auctionHouseRepository.findById(authoritativeAuctionHouseId).orElseThrow()
        authoritativeAuctionHouse.connectedId = -999
        authoritativeAuctionHouse.region = Region.Korea
        authoritativeAuctionHouse.lastModified = null
        authoritativeAuctionHouse.nextUpdate = null
        authoritativeAuctionHouse.lowestDelay = 0
        authoritativeAuctionHouse.avgDelay = 60
        authoritativeAuctionHouse.highestDelay = 0
        authoritativeAuctionHouse.updateAttempts = 0
        auctionHouseRepository.saveAndFlush(authoritativeAuctionHouse)

        connectedRealmBulkSyncService.sync(
            listOf(connectedRealm(id = 501, realmName = "Updated Realm", slug = "updated-realm")),
        )

        val repairedAuctionHouseId = auctionHouseIdFor(501)
        val repairedAuctionHouse = auctionHouseRepository.findById(repairedAuctionHouseId).orElseThrow()
        val repairedConnectedRealm = connectedRealmRepository.findById(501).orElseThrow()
        assertEquals(requireNotNull(staleAuctionHouse.id), repairedAuctionHouseId)
        assertEquals(501, repairedAuctionHouse.connectedId)
        assertEquals(Region.Europe, repairedAuctionHouse.region)
        assertEquals("updated-realm", repairedConnectedRealm.realms.single().slug)
        assertEquals(Instant.EPOCH, repairedAuctionHouse.lastModified)
        assertEquals(Instant.EPOCH, repairedAuctionHouse.nextUpdate)
        assertEquals(0L, repairedAuctionHouse.lowestDelay)
        assertEquals(60L, repairedAuctionHouse.avgDelay)
        assertEquals(0L, repairedAuctionHouse.highestDelay)
        assertEquals(0, repairedAuctionHouse.updateAttempts)
        assertEquals(1, countRowsWhere("auction_house", "connected_id = 501"))
    }

    private fun connectedRealm(
        id: Int,
        realmName: String,
        slug: String,
        extraRealmId: Int? = null,
        extraRealmName: String? = null,
        extraRealmSlug: String? = null,
    ): ConnectedRealm =
        ConnectedRealm(
            id = id,
            auctionHouse = newAuctionHouse(id),
            realms =
                buildList {
                    add(realm(id = id, name = realmName, slug = slug))
                    if (extraRealmId != null && extraRealmName != null && extraRealmSlug != null) {
                        add(realm(id = extraRealmId, name = extraRealmName, slug = extraRealmSlug))
                    }
                }.toMutableList(),
        )

    private fun realm(
        id: Int,
        name: String,
        slug: String,
    ): Realm =
        Realm(
            id = id,
            region = RegionDBO(id = 2, name = "Europe", type = Region.Europe),
            name = name,
            category = "Normal",
            locale = Locale.EN_GB,
            timezone = "UTC",
            gameBuild = GameBuildVersion.RETAIL,
            slug = slug,
        )

    private fun newAuctionHouse(id: Int): AuctionHouse =
        AuctionHouse(
            id = null,
            connectedId = id,
            region = Region.Europe,
            lastModified = null,
            lastRequested = null,
            nextUpdate = Instant.EPOCH,
            lowestDelay = 0L,
            avgDelay = 60L,
            highestDelay = 0L,
            tsmFile = null,
            statsFile = null,
            auctionFile = null,
            updateAttempts = 0,
            updateLog = mutableListOf(),
        )

    private fun seedRegion(region: Region) {
        regionRepository.save(
            RegionDBO(
                id = regionId(region),
                name = region.name,
                type = region,
            ),
        )
    }

    private fun regionId(region: Region): Int =
        when (region) {
            Region.NorthAmerica -> 1
            Region.Europe -> 2
            Region.Korea -> 3
            Region.Taiwan -> 4
        }

    private fun auctionHouseIdFor(connectedRealmId: Int): Int =
        jdbcTemplate.queryForObject(
            "SELECT auction_house_id FROM connected_realm WHERE id = ?",
            Int::class.java,
            connectedRealmId,
        )!!

    private fun execute(
        sql: String,
        vararg args: Any,
    ) {
        jdbcTemplate.update(sql, *args)
    }

    private fun countRows(tableName: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Int::class.java)!!

    private fun countRowsWhere(
        tableName: String,
        predicate: String,
    ): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName WHERE $predicate", Int::class.java)!!
}
