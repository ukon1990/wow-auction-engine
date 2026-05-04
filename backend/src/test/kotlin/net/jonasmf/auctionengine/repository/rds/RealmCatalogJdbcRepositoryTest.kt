package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.service.CommodityRealms
import net.jonasmf.auctionengine.service.ConnectedRealmBulkSyncService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

class RealmCatalogJdbcRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var realmCatalogJdbcRepository: RealmCatalogJdbcRepository

    @Autowired
    lateinit var connectedRealmBulkSyncService: ConnectedRealmBulkSyncService

    @Autowired
    lateinit var connectedRealmRepository: ConnectedRealmRepository

    @Autowired
    lateinit var regionRepository: RegionRepository

    @Autowired
    lateinit var cacheManager: CacheManager

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `findRealmCatalogRows excludes commodity realms and caches the first result`() {
        regionRepository.saveAll(
            listOf(
                RegionDBO(id = 1, name = "US", type = Region.NorthAmerica),
                RegionDBO(id = 2, name = "EU", type = Region.Europe),
            ),
        )
        regionRepository.flush()
        val eu = regionRepository.findById(2).orElseThrow()
        val us = regionRepository.findById(1).orElseThrow()

        connectedRealmRepository.save(
            connectedRealm(
                connectedRealmId = 101,
                region = eu,
                realmId = 201,
                name = "Stormrage",
                slug = "stormrage",
            ),
        )
        connectedRealmRepository.save(
            connectedRealm(
                connectedRealmId = 102,
                region = us,
                realmId = 202,
                name = "Illidan",
                slug = "illidan",
            ),
        )
        connectedRealmRepository.save(
            connectedRealm(
                connectedRealmId = CommodityRealms.idFor(Region.Europe),
                region = eu,
                realmId = -2,
                name = "Commodity",
                slug = "commodity",
                category = "Commodity",
            ),
        )
        connectedRealmRepository.flush()

        val first = realmCatalogJdbcRepository.findRealmCatalogRows()
        assertEquals(listOf("Illidan", "Stormrage"), first.map { it.name })

        jdbcTemplate.update(
            """
            INSERT INTO auction_house (
                id, auto_update, avg_delay, connected_id, game_build, highest_delay,
                lowest_delay, stats_last_modified, update_attempts
            ) VALUES (?, 0, 60, ?, 0, 0, 0, 0, 0)
            """.trimIndent(),
            103,
            103,
        )
        jdbcTemplate.update(
            """
            INSERT INTO connected_realm (id, auction_house_id)
            VALUES (?, ?)
            """.trimIndent(),
            103,
            103,
        )
        jdbcTemplate.update(
            """
            INSERT INTO realm (
                id, category, game_build, locale, name, slug, timezone, region_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            203,
            "PvE",
            0,
            Locale.EN_US.ordinal,
            "Aegwynn",
            "aegwynn",
            "America/Chicago",
            1,
        )
        jdbcTemplate.update(
            """
            INSERT INTO connected_realm_realms (connected_realm_id, realms_id)
            VALUES (?, ?)
            """.trimIndent(),
            103,
            203,
        )

        val cachedSecond = realmCatalogJdbcRepository.findRealmCatalogRows()
        assertEquals(listOf("Illidan", "Stormrage"), cachedSecond.map { it.name })

        connectedRealmBulkSyncService.sync(
            listOf(
                connectedRealm(
                    connectedRealmId = 101,
                    region = eu,
                    realmId = 201,
                    name = "Stormrage",
                    slug = "stormrage",
                ),
            ),
        )

        val refreshed = realmCatalogJdbcRepository.findRealmCatalogRows()
        assertTrue(refreshed.map { it.name }.contains("Aegwynn"))
    }

    private fun connectedRealm(
        connectedRealmId: Int,
        region: RegionDBO,
        realmId: Int,
        name: String,
        slug: String,
        category: String = "PvP",
    ): ConnectedRealm =
        ConnectedRealm(
            id = connectedRealmId,
            auctionHouse =
                AuctionHouse(
                    connectedId = connectedRealmId,
                    region = region.type,
                    lastModified = Instant.EPOCH,
                    nextUpdate = Instant.EPOCH,
                    lowestDelay = 0L,
                    avgDelay = 60L,
                    highestDelay = 0L,
                    gameBuild = 0,
                    statsLastModified = 0L,
                    updateAttempts = 0,
                ),
            realms =
                mutableListOf(
                    Realm(
                        id = realmId,
                        region = region,
                        name = name,
                        category = category,
                        locale = Locale.EN_US,
                        timezone = "America/Chicago",
                        gameBuild = GameBuildVersion.RETAIL,
                        slug = slug,
                    ),
                ),
        )
}
