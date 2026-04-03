package net.jonasmf.auctionengine.service

import aws.sdk.kotlin.services.s3.S3Client
import net.jonasmf.auctionengine.config.DynamoDbIntegrationTestBase
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.domain.AuctionHouseUpdateLog
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseDynamoRepository
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseUpdateLogDynamoRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(SpringExtension::class)
class AuctionHouseServiceTest(
    var auctionHouseService: AuctionHouseService,
    var repository: AuctionHouseDynamoRepository,
    var auctionHouseUpdateLogDynamoRepository: AuctionHouseUpdateLogDynamoRepository,
) : DynamoDbIntegrationTestBase() {
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

    @AfterEach
    fun tearDown() {
        repository.clear()
        auctionHouseUpdateLogDynamoRepository.clear()
    }

    private fun assertInstantEqualsToMillis(
        expected: Instant?,
        actual: Instant?,
    ) {
        assertEquals(expected?.toEpochMilliseconds(), actual?.toEpochMilliseconds())
    }

    private fun assertInstantCloseTo(
        expected: Instant?,
        actual: Instant?,
        toleranceMs: Long = 2_000,
    ) {
        val expectedMillis = expected?.toEpochMilliseconds()
        val actualMillis = actual?.toEpochMilliseconds()
        requireNotNull(expectedMillis)
        requireNotNull(actualMillis)
        assertTrue { kotlin.math.abs(expectedMillis - actualMillis) <= toleranceMs }
    }

    @Nested
    inner class UpdateTimes {
        @Test
        fun `should fallback to 60 minutes avg `() {
        }

        @Test
        fun `Should set no longer than 120 minutes avg delay`() {
        }

        @Test
        fun `should be no less than 30 minutes avg delay`() {
        }

        @Test
        fun `should update the next update time, based on avg delay + last modified on successful update`() {
            val originalState = auctionHouses.find { it.id == 1 }
            val newLastModified = originalState?.lastModified?.plus(60.minutes)
            auctionHouseService.updateTimes(1, newLastModified, true)

            val result = repository.findById(1).get()
            assertInstantEqualsToMillis(newLastModified, result.lastModified)
            assertEquals(60, result.avgDelay)
            assertInstantCloseTo(getOffsetFromNow(60), result.nextUpdate)

            // Should also add a new entry into last modified log

            val logEntries = auctionHouseUpdateLogDynamoRepository.findByIdAndMostRecentLastModified(1)
            assertEquals(2, logEntries.size)
        }

        @Test
        fun `should add a delay based on the number of failed attempts`() {
            val originalState = auctionHouses.find { it.id == 1 }
            auctionHouseService.updateTimes(1, null, false)

            val result = repository.findById(1).get()
            assertInstantEqualsToMillis(originalState?.lastModified, result.lastModified)
            assertTrue {
                result.nextUpdate?.toEpochMilliseconds()!! > originalState?.nextUpdate?.toEpochMilliseconds()!!
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
