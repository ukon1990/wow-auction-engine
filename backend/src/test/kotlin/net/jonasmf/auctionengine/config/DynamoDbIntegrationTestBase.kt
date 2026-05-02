package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseUpdateLogDynamo
import net.jonasmf.auctionengine.testsupport.database.TestDataCleaner
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

abstract class DynamoDbIntegrationTestBase(
    private val cleaner: TestDataCleaner,
) : FlociIntegrationTestBase() {
    @BeforeEach
    fun cleanTablesBeforeEach() {
        cleaner.clearDynamoTable(AuctionHouseDynamo::class.java)
        cleaner.clearDynamoTable(AuctionHouseUpdateLogDynamo::class.java)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerDynamoDbProperties(registry: DynamicPropertyRegistry) {
            registerFlociCredentials(registry)
            registry.add("spring.cloud.aws.dynamodb.endpoint") {
                "http://${flociContainer.host}:${flociContainer.getMappedPort(4566)}"
            }
        }
    }
}
