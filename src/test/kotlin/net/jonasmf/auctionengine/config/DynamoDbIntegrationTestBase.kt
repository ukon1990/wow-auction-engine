package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseUpdateLogDynamo
import net.jonasmf.auctionengine.testsupport.database.DynamoDBUtil
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

abstract class DynamoDbIntegrationTestBase(
    private val dbUtil: DynamoDBUtil,
) : FlociIntegrationTestBase() {
    @BeforeEach
    fun cleanTablesBeforeEach() {
        dbUtil.clearTable(AuctionHouseDynamo::class.java)
        dbUtil.clearTable(AuctionHouseUpdateLogDynamo::class.java)
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
