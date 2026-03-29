package net.jonasmf.auctionengine.service

import aws.sdk.kotlin.services.s3.S3Client
import net.jonasmf.auctionengine.config.DynamoDbIntegrationTestBase
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseDynamoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.temporal.ChronoUnit
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class AuctionHouseServiceTest : DynamoDbIntegrationTestBase() {
    @MockitoBean
    lateinit var amazonS3: S3Client

    @MockitoBean
    lateinit var connectedRealmService: ConnectedRealmService

    @Autowired
    lateinit var repository: AuctionHouseDynamoRepository

    @Autowired
    lateinit var auctionHouseService: AuctionHouseService

    val auctionHouses = listOf<AuctionHouse>(
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
            id = 4,
            autoUpdate = true,
            region = Region.Europe,
            avgDelay = 60,
            nextUpdate = getOffsetFromNow(-20),
            lastModified = getOffsetFromNow(-80),
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

    fun getOffsetFromNow(minutes: Int): Instant {
        return Clock.System.now().plus(minutes.minutes)
    }

    @BeforeEach
    fun setUp() {
        auctionHouses.forEach { repository.save(it) }
    }

    @Nested
    inner class UpdateTimes() {
        @Test
        fun `should update the next update time, based on avg delay + last modified on successful update`() {
            val originalState = auctionHouses.find { it.id == 1 }
            val newLastModified = originalState?.lastModified?.plus(60.minutes)
            auctionHouseService.updateTimes(1, newLastModified, true)

            val result = repository.findById(1).get()
            assertEquals(newLastModified, result.lastModified)
            assertEquals(60, result.avgDelay)
            assertEquals(getOffsetFromNow(60), result.nextUpdate)
        }
    }

    @Nested
    inner class GetReadyForUpdate() {
        @Test
        fun `should only return auction houses for the given region where an update is due`() {
            val result = auctionHouseService.getReadyForUpdate(Region.Europe)

            assertEquals(6, result.first().id)
            assertEquals(4, result.last().id)
            assertEquals(3, result.size)
        }
    }
}
