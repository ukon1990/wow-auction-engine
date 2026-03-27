package net.jonasmf.auctionengine.service

import aws.sdk.kotlin.services.s3.S3Client
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.TestcontainersConfiguration
import net.jonasmf.auctionengine.config.StubAuthWebClientConfig
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseDynamoRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.Assert
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer

@Import(TestcontainersConfiguration::class, StubAuthWebClientConfig::class)
@SpringBootTest(properties = ["spring.task.scheduling.enabled=false"])
@ActiveProfiles("test")
class AuctionHouseServiceTest {
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
        ),
        AuctionHouseDynamo(
            id = 2,
            autoUpdate = true,
            region = Region.NorthAmerica,
        ),
    )

    @BeforeEach
    fun setUp() {
        auctionHouses.forEach { repository.save(it) }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerDynamoDbProperties(registry: DynamicPropertyRegistry) {
            val localStack = TestcontainersConfiguration.localStackContainer
            if (!localStack.isRunning) {
                localStack.start()
            }

            registry.add("spring.cloud.aws.dynamodb.endpoint") {
                localStack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString()
            }
            registry.add("spring.cloud.aws.credentials.access-key") { localStack.accessKey }
            registry.add("spring.cloud.aws.credentials.secret-key") { localStack.secretKey }
        }
    }

    @Nested
    inner class GetReadyForUpdate() {
        @Test
        fun canGetAuctionHouses() {
            val result = auctionHouseService.getReadyForUpdate(Region.Europe)
            Assert.assertEquals(1, result.size)
        }
    }
}
