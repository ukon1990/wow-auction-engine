package net.jonasmf.auctionengine.service

import aws.sdk.kotlin.services.s3.S3Client
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.domain.realm.AuctionHouse
import net.jonasmf.auctionengine.repository.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.repository.rds.RegionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse as RealmAuctionHouse
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository as AuctionHouseEntityRepository

class AuctionHouseServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var auctionHouseService: AuctionHouseService

    @Autowired
    lateinit var repository: AuctionHouseRepository

    @Autowired
    lateinit var auctionHouseEntityRepository: AuctionHouseEntityRepository

    @Autowired
    lateinit var connectedRealmUpdateHistoryService: ConnectedRealmUpdateHistoryService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var connectedRealmRepository: ConnectedRealmRepository

    @Autowired
    lateinit var regionRepository: RegionRepository

    @MockitoBean
    lateinit var amazonS3: S3Client

    @MockitoBean
    lateinit var connectedRealmService: ConnectedRealmService

    private val auctionHouseIdWithLogs = 4
    private val auctionHouseIdWithLogsLastModified = getOffsetFromNow(-80)

    private val auctionHouses =
        listOf(
            AuctionHouse(
                id = 1,
                connectedId = 1,
                autoUpdate = true,
                region = Region.Korea,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(-10),
                lastModified = getOffsetFromNow(-70),
            ),
            AuctionHouse(
                id = 2,
                connectedId = 2,
                autoUpdate = true,
                region = Region.Korea,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(10),
                lastModified = getOffsetFromNow(-70),
            ),
            AuctionHouse(
                id = 3,
                connectedId = 3,
                autoUpdate = true,
                region = Region.Europe,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(10),
                lastModified = getOffsetFromNow(-50),
            ),
            AuctionHouse(
                id = auctionHouseIdWithLogs,
                connectedId = auctionHouseIdWithLogs,
                autoUpdate = true,
                region = Region.Europe,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(-20),
                lastModified = auctionHouseIdWithLogsLastModified,
            ),
            AuctionHouse(
                id = 5,
                connectedId = 5,
                autoUpdate = true,
                region = Region.Europe,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(-50),
                lastModified = getOffsetFromNow(-100),
            ),
            AuctionHouse(
                id = 6,
                connectedId = 6,
                autoUpdate = true,
                region = Region.Europe,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(-90),
                lastModified = getOffsetFromNow(-120),
            ),
        )

    private fun getOffsetFromNow(minutes: Int): Instant = Clock.System.now().plus(minutes.minutes)

    @BeforeEach
    fun setUp() {
        testDataCleaner.resetRelationalDatabase()
        seedRegion(Region.Europe, 2)
        seedRegion(Region.Korea, 3)
        auctionHouses.forEach {
            repository.save(it)
            ensureConnectedRealm(it)
        }
        List(10) {
            seedUpdateHistory(
                auctionHouseIdWithLogs,
                auctionHouseIdWithLogsLastModified.minus((it * 60).minutes),
            )
        }
    }

    @Nested
    inner class CreateIfMissing {
        @Test
        fun `should seed new auction houses as immediately ready for update`() {
            val connectedRealm =
                ConnectedRealm(
                    id = 999,
                    auctionHouse =
                        RealmAuctionHouse(
                            connectedId = 999,
                            region = Region.Europe,
                            lastModified = null,
                            lastRequested = null,
                            nextUpdate = java.time.Instant.EPOCH,
                            lowestDelay = 60,
                            avgDelay = 60,
                            highestDelay = 60,
                            updateAttempts = 0,
                        ),
                    realms =
                        mutableListOf(
                            Realm(
                                id = 999,
                                region = RegionDBO(id = 2, name = "Europe", type = Region.Europe),
                                name = "Test Realm",
                                category = "Normal",
                                locale = Locale.EN_GB,
                                timezone = "UTC",
                                gameBuild = GameBuildVersion.RETAIL,
                                slug = "test-realm",
                            ),
                        ),
                )
            connectedRealmRepository.save(connectedRealm)

            auctionHouseService.createIfMissing(connectedRealm)

            val saved = repository.findById(999)
            assertEquals(999, saved?.connectedId ?: 0)
            assertNotNull(saved?.nextUpdate)
            assertEquals(0L, saved?.nextUpdate!!.epochSeconds)
            assertEquals(1, auctionHouseService.getReadyForUpdate(Region.Europe).count { it.id == 999 })
        }

        @Test
        fun `should repair existing auction house metadata from connected realm`() {
            val connectedRealm =
                ConnectedRealm(
                    id = 1000,
                    auctionHouse =
                        RealmAuctionHouse(
                            connectedId = 0,
                            region = Region.Korea,
                            lastModified = null,
                            lastRequested = null,
                            nextUpdate = null,
                            lowestDelay = 0,
                            avgDelay = 60,
                            highestDelay = 0,
                            updateAttempts = 0,
                        ),
                    realms =
                        mutableListOf(
                            Realm(
                                id = 1000,
                                region = RegionDBO(id = 2, name = "Europe", type = Region.Europe),
                                name = "Repaired Realm",
                                category = "Normal",
                                locale = Locale.EN_GB,
                                timezone = "UTC",
                                gameBuild = GameBuildVersion.RETAIL,
                                slug = "repaired-realm",
                            ),
                        ),
                )
            connectedRealmRepository.save(connectedRealm)

            auctionHouseService.createIfMissing(connectedRealmRepository.findById(1000).orElseThrow())

            val saved = repository.findById(1000)
            assertEquals(1000, saved?.connectedId)
            assertEquals(Region.Europe, saved?.region)
            assertEquals(0L, saved?.lastModified!!.epochSeconds)
            assertEquals(0L, saved.nextUpdate!!.epochSeconds)
            assertEquals(60L, saved.avgDelay)
            assertEquals(0L, saved.lowestDelay)
            assertEquals(0L, saved.highestDelay)
            assertEquals(0, saved.updateAttempts)
        }
    }

    @Nested
    inner class UpdateTimes {
        @Test
        fun `should set next update time and calculate rounded delay summary on update`() {
            val connectedRealmId = 5
            var house = repository.findById(connectedRealmId)
            var lastModified = requireNotNull(house?.lastModified)
            seedUpdateHistory(connectedRealmId, lastModified)
            seedUpdateHistory(connectedRealmId, lastModified.plus(35.minutes))

            auctionHouseService.updateTimes(
                connectedRealmId,
                lastModified.plus(35.minutes),
                true,
            )
            lastModified = requireNotNull(repository.findById(connectedRealmId)?.lastModified)
            seedUpdateHistory(connectedRealmId, lastModified.plus(48.minutes))

            auctionHouseService.updateTimes(
                connectedRealmId,
                lastModified.plus(48.minutes),
                true,
            )

            house = repository.findById(connectedRealmId)
            assertEquals(35, house?.lowestDelay)
            assertEquals(42, house?.avgDelay)
            assertEquals(48, house?.highestDelay)
            assertTrue(house?.nextUpdate!!.toEpochMilliseconds() > house.lastModified!!.toEpochMilliseconds())
            assertEquals(35, house.nextUpdate!!.minus(house.lastModified!!).inWholeMinutes)
        }

        @Test
        fun `should default delay summary to 45 when no usable connected realm history values are present`() {
            val connectedRealmId = 1001
            connectedRealmRepository.save(
                connectedRealm(
                    AuctionHouse(
                        id = connectedRealmId,
                        connectedId = connectedRealmId,
                        autoUpdate = true,
                        region = Region.Europe,
                        avgDelay = 60,
                        nextUpdate = getOffsetFromNow(-10),
                        lastModified = null,
                    ),
                ),
            )

            auctionHouseService.updateTimes(
                connectedRealmId,
                getOffsetFromNow(-5),
                true,
            )

            val result = repository.findById(connectedRealmId)
            assertEquals(45, result?.lowestDelay)
            assertEquals(45, result?.avgDelay)
            assertEquals(45, result?.highestDelay)
            assertEquals(45, result?.nextUpdate!!.minus(result.lastModified!!).inWholeMinutes)
        }

        @Test
        fun `should ignore connected realm history rows older than 72 hours when calculating delay summary`() {
            val connectedRealmId = 1002
            seedAuctionHouseWithConnectedRealm(
                AuctionHouse(
                    id = connectedRealmId,
                    connectedId = connectedRealmId,
                    autoUpdate = true,
                    region = Region.Europe,
                    avgDelay = 60,
                    nextUpdate = getOffsetFromNow(-10),
                    lastModified = getOffsetFromNow(-4400),
                ),
            )

            var lastModified = requireNotNull(repository.findById(connectedRealmId)?.lastModified)
            seedUpdateHistory(connectedRealmId, lastModified.plus(20.minutes))
            auctionHouseService.updateTimes(
                connectedRealmId,
                lastModified.plus(20.minutes),
                true,
            )
            lastModified = requireNotNull(repository.findById(connectedRealmId)?.lastModified)
            seedUpdateHistory(connectedRealmId, lastModified.plus(70.minutes))

            auctionHouseService.updateTimes(
                connectedRealmId,
                lastModified.plus(70.minutes),
                true,
            )

            val result = repository.findById(connectedRealmId)
            assertEquals(70, result?.lowestDelay)
            assertEquals(70, result?.avgDelay)
            assertEquals(70, result?.highestDelay)
            assertEquals(70, result?.nextUpdate!!.minus(result.lastModified!!).inWholeMinutes)
        }

        @Test
        fun `should clamp min and max delay stats on update`() {
            val connectedRealmId = 1003
            seedAuctionHouseWithConnectedRealm(
                AuctionHouse(
                    id = connectedRealmId,
                    connectedId = connectedRealmId,
                    autoUpdate = true,
                    region = Region.Europe,
                    avgDelay = 60,
                    nextUpdate = getOffsetFromNow(-10),
                    lastModified = getOffsetFromNow(-300),
                ),
            )

            var lastModified = requireNotNull(repository.findById(connectedRealmId)?.lastModified)
            seedUpdateHistory(connectedRealmId, lastModified)
            seedUpdateHistory(connectedRealmId, lastModified.plus(20.minutes))
            auctionHouseService.updateTimes(
                connectedRealmId,
                lastModified.plus(20.minutes),
                true,
            )
            lastModified = requireNotNull(repository.findById(connectedRealmId)?.lastModified)
            seedUpdateHistory(connectedRealmId, lastModified.plus(130.minutes))

            auctionHouseService.updateTimes(
                connectedRealmId,
                lastModified.plus(130.minutes),
                true,
            )

            val result = repository.findById(connectedRealmId)
            assertEquals(30, result?.lowestDelay)
            assertEquals(75, result?.avgDelay)
            assertEquals(120, result?.highestDelay)
            assertEquals(30, result?.nextUpdate!!.minus(result.lastModified!!).inWholeMinutes)
        }

        @Test
        fun `should update the next update time without writing redundant history`() {
            val connectedRealmId = 1004
            seedAuctionHouseWithConnectedRealm(
                AuctionHouse(
                    id = connectedRealmId,
                    connectedId = connectedRealmId,
                    autoUpdate = true,
                    region = Region.Europe,
                    avgDelay = 60,
                    nextUpdate = getOffsetFromNow(-10),
                    lastModified = getOffsetFromNow(-300),
                ),
            )

            var lastModified = requireNotNull(repository.findById(connectedRealmId)?.lastModified)
            seedUpdateHistory(connectedRealmId, lastModified)
            seedUpdateHistory(connectedRealmId, lastModified.plus(60.minutes))
            auctionHouseService.updateTimes(
                connectedRealmId,
                lastModified.plus(60.minutes),
                true,
            )
            lastModified = requireNotNull(repository.findById(connectedRealmId)?.lastModified)

            val newLastModified = lastModified.plus(60.minutes)
            seedUpdateHistory(connectedRealmId, newLastModified)
            val historyCountBefore = countUpdateHistoryRows(connectedRealmId)
            auctionHouseService.updateTimes(connectedRealmId, newLastModified, true)

            val result = repository.findById(connectedRealmId)
            assertEquals(newLastModified, result?.lastModified)
            assertTrue(result?.lastModified!! < result.nextUpdate!!)
            assertEquals(60, result.lowestDelay)
            assertEquals(60, result.avgDelay)
            assertEquals(60, result.highestDelay)
            assertEquals(60, result.nextUpdate!!.minus(result.lastModified!!).inWholeMinutes)
            assertEquals(historyCountBefore, countUpdateHistoryRows(connectedRealmId))
        }

        @Test
        fun `should add a delay based on the number of failed attempts`() {
            val originalState = repository.findById(1)
            val historyCountBefore = countUpdateHistoryRows(1)
            auctionHouseService.updateTimes(1, null, false)

            val result = repository.findById(1)
            assertEquals(originalState?.lastModified, result?.lastModified)
            assertTrue(result?.nextUpdate!!.toEpochMilliseconds() > originalState?.nextUpdate!!.toEpochMilliseconds())
            assertEquals(historyCountBefore, countUpdateHistoryRows(1))
        }
    }

    @Nested
    inner class GetReadyForUpdate {
        @Test
        fun `should only return auction houses for the given region where an update is due`() {
            val result = auctionHouseService.getReadyForUpdate(Region.Europe)

            assertEquals(6, result.first().id)
            assertEquals(4, result.last().id)
            assertEquals(3, result.size)
        }
    }

    @Nested
    inner class GetReadyForHourlyStatsCleanup {
        @Test
        fun `should only return those with older delete lastHistoryDeleteEvent`() {
            val lastDeletedTime = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2)
            val hourlyTTL = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)
            val house = auctionHouses.first()
            auctionHouseService.updateLastDailyPriceDeleted(house.connectedId, lastDeletedTime)
            val result = auctionHouseService.getReadyForHourlyStatsCleanup(hourlyTTL)
            assertEquals(1, result.size)
        }
    }

    private fun seedUpdateHistory(
        connectedRealmId: Int,
        lastModified: Instant,
    ) {
        val connectedRealm = connectedRealmRepository.findById(connectedRealmId).orElseThrow()
        connectedRealmUpdateHistoryService.startUpdate(
            connectedRealm = connectedRealm,
            auctionCount = 1,
            lastModified = lastModified.toJavaInstant().atZone(ZoneOffset.UTC),
        )
    }

    private fun countUpdateHistoryRows(connectedRealmId: Int): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM connected_realm_update_history WHERE connected_realm_id = ?",
            Int::class.java,
            connectedRealmId,
        ) ?: 0

    private fun seedAuctionHouseWithConnectedRealm(auctionHouse: AuctionHouse) {
        repository.save(auctionHouse)
        ensureConnectedRealm(auctionHouse)
    }

    private fun ensureConnectedRealm(auctionHouse: AuctionHouse) {
        val connectedId = requireNotNull(auctionHouse.id)
        if (connectedRealmRepository.findById(connectedId).isPresent) return

        val persistedAuctionHouse = auctionHouseEntityRepository.findByConnectedId(connectedId).orElseThrow()
        connectedRealmRepository.save(
            connectedRealm(
                auctionHouse = auctionHouse,
                persistedAuctionHouse = persistedAuctionHouse,
            ),
        )
    }

    private fun connectedRealm(auctionHouse: AuctionHouse): ConnectedRealm =
        connectedRealm(
            auctionHouse = auctionHouse,
            persistedAuctionHouse = null,
        )

    private fun connectedRealm(
        auctionHouse: AuctionHouse,
        persistedAuctionHouse: RealmAuctionHouse?,
    ): ConnectedRealm =
        ConnectedRealm(
            id = requireNotNull(auctionHouse.id),
            auctionHouse =
                persistedAuctionHouse
                    ?: RealmAuctionHouse(
                        connectedId = requireNotNull(auctionHouse.id),
                        region = auctionHouse.region,
                        lastModified = auctionHouse.lastModified?.toJavaInstant(),
                        lastRequested = auctionHouse.lastRequested?.toJavaInstant(),
                        nextUpdate = auctionHouse.nextUpdate?.toJavaInstant(),
                        lowestDelay = auctionHouse.lowestDelay,
                        avgDelay = auctionHouse.avgDelay,
                        highestDelay = auctionHouse.highestDelay,
                        updateAttempts = auctionHouse.updateAttempts,
                    ),
            realms =
                mutableListOf(
                    Realm(
                        id = requireNotNull(auctionHouse.id),
                        region =
                            RegionDBO(
                                id =
                                    if (auctionHouse.region ==
                                        Region.Korea
                                    ) {
                                        3
                                    } else {
                                        2
                                    },
                                name = auctionHouse.region.name,
                                type = auctionHouse.region,
                            ),
                        name = "Realm ${auctionHouse.id}",
                        category = "Normal",
                        locale = Locale.EN_GB,
                        timezone = "UTC",
                        gameBuild = GameBuildVersion.RETAIL,
                        slug = "realm-${auctionHouse.id}",
                    ),
                ),
        )

    private fun seedRegion(
        region: Region,
        id: Int,
    ) {
        if (regionRepository.findById(id).isEmpty) {
            regionRepository.save(RegionDBO(id = id, name = region.name, type = region))
        }
    }
}
