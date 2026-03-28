package net.jonasmf.auctionengine.service

import aws.sdk.kotlin.services.s3.S3Client
import net.jonasmf.auctionengine.config.DynamoDbIntegrationTestBase
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseDynamoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.ZonedDateTime

class AuctionHouseServiceTest : DynamoDbIntegrationTestBase() {
    @MockitoBean
    lateinit var amazonS3: S3Client

    @MockitoBean
    lateinit var connectedRealmService: ConnectedRealmService

    @Autowired
    lateinit var repository: AuctionHouseDynamoRepository

    @Autowired
    lateinit var auctionHouseService: AuctionHouseService

    val auctionHouses = listOf<AuctionHouseDynamo>(
        AuctionHouseDynamo(
            id = 1,
            autoUpdate = true,
            region = Region.Korea,
            avgDelay = 60,
            nextUpdate = getOffsetFromNow(-10),
            lastModified = getOffsetFromNow(-70),
        ),
        AuctionHouseDynamo(
            id = 2,
            autoUpdate = true,
            region = Region.Korea,
            avgDelay = 60,
            nextUpdate = getOffsetFromNow(10),
            lastModified = getOffsetFromNow(-70),
        ),
        AuctionHouseDynamo(
            id = 3,
            autoUpdate = true,
            region = Region.Europe,
            avgDelay = 60,
            nextUpdate = getOffsetFromNow(10),
            lastModified = getOffsetFromNow(-50),
        ),
        AuctionHouseDynamo(
            id = 4,
            autoUpdate = true,
            region = Region.Europe,
            avgDelay = 60,
            nextUpdate = getOffsetFromNow(-20),
            lastModified = getOffsetFromNow(-80),
        ),
        AuctionHouseDynamo(
            id = 5,
            autoUpdate = true,
            region = Region.Europe,
            avgDelay = 60,
            nextUpdate = getOffsetFromNow(-50),
            lastModified = getOffsetFromNow(-100),
        ),
        AuctionHouseDynamo(
            id = 6,
            autoUpdate = true,
            region = Region.Europe,
            avgDelay = 60,
            nextUpdate = getOffsetFromNow(-90),
            lastModified = getOffsetFromNow(-120),
        ),
    )

    fun getOffsetFromNow(minutes: Int): Long {
        return ZonedDateTime.now().toEpochSecond() + (minutes * 60)
    }

    @BeforeEach
    fun setUp() {
        auctionHouses.forEach { repository.save(it) }
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
