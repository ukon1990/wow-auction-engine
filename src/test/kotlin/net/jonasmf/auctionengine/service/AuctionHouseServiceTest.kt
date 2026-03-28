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
            region = Region.Europe,
            avgDelay = 60,
            nextUpdate = getOffsetFromNow(10),
        ),
        AuctionHouseDynamo(
            id = 2,
            autoUpdate = true,
            region = Region.NorthAmerica,
            avgDelay = 60,
            nextUpdate = getOffsetFromNow(10),
        ),
        AuctionHouseDynamo(
            id = 1,
            autoUpdate = true,
            region = Region.Europe,
            avgDelay = 60,
            nextUpdate = getOffsetFromNow(10),
        ),
    )

    fun getOffsetFromNow(minutes: Int): Long {
        return ZonedDateTime.now().toEpochSecond() - (minutes * 60)
    }

    @BeforeEach
    fun setUp() {
        auctionHouses.forEach { repository.save(it) }
    }

    @Nested
    inner class GetReadyForUpdate() {
        @Test
        fun canGetAuctionHouses() {
            val result = auctionHouseService.getReadyForUpdate(Region.Europe)
            assertEquals(1, result.size)
        }
    }
}
