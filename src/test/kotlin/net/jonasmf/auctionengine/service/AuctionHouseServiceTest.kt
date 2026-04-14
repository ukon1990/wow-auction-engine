package net.jonasmf.auctionengine.service

import aws.sdk.kotlin.services.s3.S3Client
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.domain.AuctionHouseUpdateLog
import net.jonasmf.auctionengine.repository.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.AuctionHouseUpdateLogRepository
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.repository.rds.RegionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse as RealmAuctionHouse

class AuctionHouseServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var auctionHouseService: AuctionHouseService

    @Autowired
    lateinit var repository: AuctionHouseRepository

    @Autowired
    lateinit var auctionHouseUpdateLogRepository: AuctionHouseUpdateLogRepository

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

    private val updateLogs =
        List(10) {
            AuctionHouseUpdateLog(
                id = auctionHouseIdWithLogs,
                lastModified = auctionHouseIdWithLogsLastModified.minus((it * 60).minutes),
                size = 1.0,
                url = "",
                timeSincePreviousDump = 0,
            )
        }

    private fun getOffsetFromNow(minutes: Int): Instant = Clock.System.now().plus(minutes.minutes)

    @BeforeEach
    fun setUp() {
        seedRegion(Region.Europe, 2)
        seedRegion(Region.Korea, 3)
        auctionHouses.forEach { repository.save(it) }
        updateLogs.forEach {
            auctionHouseUpdateLogRepository.save(
                it.id,
                it.lastModified,
                it.size,
                it.url,
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
                            tsmFile = null,
                            statsFile = null,
                            auctionFile = null,
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

            val saved = repository.findById(999).orElseThrow()
            assertEquals(999, saved.connectedId)
            assertNotNull(saved.nextUpdate)
            assertEquals(0L, saved.nextUpdate!!.epochSeconds)
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
                            tsmFile = null,
                            statsFile = null,
                            auctionFile = null,
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

            val saved = repository.findById(1000).orElseThrow()
            assertEquals(1000, saved.connectedId)
            assertEquals(Region.Europe, saved.region)
            assertEquals(0L, saved.lastModified!!.epochSeconds)
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
        fun `Should set next update time and calculate delay summary on update`() {
            val connectedRealmId = 1
            var house = repository.findById(connectedRealmId).get()
            var startTime = house.lastModified

            auctionHouseService.updateTimes(
                connectedRealmId,
                startTime?.plus(30.minutes),
                true,
                "https://example.json/1",
            )
            startTime = repository.findById(connectedRealmId).get().lastModified

            auctionHouseService.updateTimes(
                connectedRealmId,
                startTime?.plus(90.minutes),
                true,
                "https://example.json/2",
            )
            startTime = repository.findById(connectedRealmId).get().lastModified

            List(10) {
                auctionHouseService.updateTimes(
                    connectedRealmId,
                    startTime?.plus((it * 45).minutes),
                    true,
                    "https://example.json/3",
                )
            }

            house = repository.findById(connectedRealmId).get()
            assertEquals(30, house.lowestDelay)
            assertEquals(48, house.avgDelay)
            assertEquals(90, house.highestDelay)
            assertTrue(house.nextUpdate!!.toEpochMilliseconds() > house.lastModified!!.toEpochMilliseconds())
            assertEquals(30, house.nextUpdate!!.minus(house.lastModified!!).inWholeMinutes)
        }

        @Test
        fun `should update the next update time, and add a new log entry for successful updates`() {
            val originalState = repository.findById(1).get()
            val newLastModified = originalState.lastModified?.plus(60.minutes)
            auctionHouseService.updateTimes(1, newLastModified, true, "https://example.json")

            val result = repository.findById(1).get()
            assertEquals(newLastModified, result.lastModified)
            assertTrue(result.lastModified!! < result.nextUpdate!!)

            val logEntries = auctionHouseUpdateLogRepository.findByIdAndMostRecentLastModified(1)
            assertEquals(2, logEntries.size)
        }

        @Test
        fun `should add a delay based on the number of failed attempts`() {
            val originalState = repository.findById(1).get()
            auctionHouseService.updateTimes(1, null, false)

            val result = repository.findById(1).get()
            assertEquals(originalState.lastModified, result.lastModified)
            assertTrue(result.nextUpdate!!.toEpochMilliseconds() > originalState.nextUpdate!!.toEpochMilliseconds())
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

    private fun connectedRealm(auctionHouse: AuctionHouse): ConnectedRealm =
        ConnectedRealm(
            id = requireNotNull(auctionHouse.id),
            auctionHouse =
                RealmAuctionHouse(
                    connectedId = requireNotNull(auctionHouse.id),
                    region = auctionHouse.region,
                    lastModified = auctionHouse.lastModified?.toJavaInstant(),
                    lastRequested = auctionHouse.lastRequested?.toJavaInstant(),
                    nextUpdate = auctionHouse.nextUpdate?.toJavaInstant(),
                    lowestDelay = auctionHouse.lowestDelay,
                    avgDelay = auctionHouse.avgDelay,
                    highestDelay = auctionHouse.highestDelay,
                    tsmFile = null,
                    statsFile = null,
                    auctionFile = null,
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
