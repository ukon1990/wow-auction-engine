package net.jonasmf.auctionengine.service

import aws.sdk.kotlin.services.s3.S3Client
import net.jonasmf.auctionengine.config.DynamoDbIntegrationTestBase
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.domain.AuctionHouseUpdateLog
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseDynamoRepository
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseUpdateLogDynamoRepository
import net.jonasmf.auctionengine.testsupport.database.TestDataCleaner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.ZonedDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse as RealmAuctionHouse

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(SpringExtension::class)
class AuctionHouseServiceTest(
    private val cleaner: TestDataCleaner,
    private var auctionHouseService: AuctionHouseService,
    private var repository: AuctionHouseDynamoRepository,
    private var auctionHouseUpdateLogDynamoRepository: AuctionHouseUpdateLogDynamoRepository,
) : DynamoDbIntegrationTestBase(cleaner) {
    @MockitoBean
    lateinit var amazonS3: S3Client

    @MockitoBean
    lateinit var connectedRealmService: ConnectedRealmService

    val auctionHouseIdWithLogs = 4
    val auctionHouseIdWithLogsLastModified = getOffsetFromNow(-80)

    val auctionHouses =
        listOf<AuctionHouse>(
            AuctionHouse(
                id = 1,
                autoUpdate = true,
                region = Region.Korea,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(-10),
                lastModified = getOffsetFromNow(-70),
            ),
            AuctionHouse(
                id = 2,
                autoUpdate = true,
                region = Region.Korea,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(10),
                lastModified = getOffsetFromNow(-70),
            ),
            AuctionHouse(
                id = 3,
                autoUpdate = true,
                region = Region.Europe,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(10),
                lastModified = getOffsetFromNow(-50),
            ),
            AuctionHouse(
                id = auctionHouseIdWithLogs,
                autoUpdate = true,
                region = Region.Europe,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(-20),
                lastModified = auctionHouseIdWithLogsLastModified,
            ),
            AuctionHouse(
                id = 5,
                autoUpdate = true,
                region = Region.Europe,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(-50),
                lastModified = getOffsetFromNow(-100),
            ),
            AuctionHouse(
                id = 6,
                autoUpdate = true,
                region = Region.Europe,
                avgDelay = 60,
                nextUpdate = getOffsetFromNow(-90),
                lastModified = getOffsetFromNow(-120),
            ),
        )

    var updateLogs: List<AuctionHouseUpdateLog> =
        List<AuctionHouseUpdateLog>(10) {
            AuctionHouseUpdateLog(
                id = auctionHouseIdWithLogs,
                lastModified = auctionHouseIdWithLogsLastModified.minus((it * 60).minutes),
                size = 1.0,
                url = "",
                timeSincePreviousDump = 0, // Not relevant, the repo sets it.
            )
        }

    fun getOffsetFromNow(minutes: Int): Instant = Clock.System.now().plus(minutes.minutes)

    @BeforeEach
    fun setUp() {
        auctionHouses.forEach { repository.save(it) }
        updateLogs.forEach {
            auctionHouseUpdateLogDynamoRepository.save(
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
                            lastModified = null,
                            lastRequested = null,
                            nextUpdate = ZonedDateTime.now(),
                            lowestDelay = 60,
                            averageDelay = 60,
                            highestDelay = 60,
                            tsmFile = null,
                            statsFile = null,
                            auctionFile = null,
                            failedAttempts = 0,
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

            auctionHouseService.createIfMissing(connectedRealm)

            val saved = repository.findById(999).orElseThrow()
            assertEquals(999, saved.connectedId)
            assertNotNull(saved.nextUpdate)
            assertEquals(0L, saved.nextUpdate!!.epochSeconds)
            assertEquals(1, auctionHouseService.getReadyForUpdate(Region.Europe).count { it.id == 999 })
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

            List<Unit>(10) {
                auctionHouseService.updateTimes(
                    connectedRealmId,
                    startTime?.plus((it * 45).minutes),
                    true,
                    "https://example.json/3",
                )
            }

            house = repository.findById(connectedRealmId).get()
            assertEquals(30, house.lowestDelay)
            assertEquals(48, house.avgDelay) // Should be 47,5 but rounds up
            assertEquals(90, house.highestDelay)
            assertTrue(
                house.nextUpdate!!.toEpochMilliseconds() > house.lastModified!!.toEpochMilliseconds(),
            ) { "Next update(${house.nextUpdate}) should be after last modified(${house.lastModified})" }
            assertEquals(30, house.nextUpdate!!.minus(house.lastModified!!).inWholeMinutes)
        }

        @Test
        fun `should update the next update time, and add a new log entry for successful updates`() {
            val originalState = repository.findById(1).get()
            val newLastModified = originalState.lastModified?.plus(60.minutes)
            auctionHouseService.updateTimes(1, newLastModified, true, "https://example.json")

            val result = repository.findById(1).get()
            assertEquals(newLastModified, result.lastModified)
            assertTrue(result.lastModified!! < result.nextUpdate!!) {
                "The next update time (${result.nextUpdate}) should always be after last modified (${result.lastModified})"
            }

            // Should also add a new entry into last modified log
            val logEntries = auctionHouseUpdateLogDynamoRepository.findByIdAndMostRecentLastModified(1)
            assertEquals(2, logEntries.size)
        }

        @Test
        fun `should add a delay based on the number of failed attempts`() {
            val originalState = repository.findById(1).get()
            auctionHouseService.updateTimes(1, null, false)

            val result = repository.findById(1).get()
            assertEquals(originalState.lastModified, result.lastModified)
            assertTrue {
                result.nextUpdate?.toEpochMilliseconds()!! > originalState.nextUpdate?.toEpochMilliseconds()!!
            }
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
}
