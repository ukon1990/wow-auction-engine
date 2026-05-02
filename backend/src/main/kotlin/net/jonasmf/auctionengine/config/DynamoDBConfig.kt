package net.jonasmf.auctionengine.config

import io.awspring.cloud.dynamodb.DefaultDynamoDbTableNameResolver
import io.awspring.cloud.dynamodb.DynamoDbTableNameResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseUpdateLogDynamo
import net.jonasmf.auctionengine.repository.dynamodb.AUCTION_HOUSE_TABLE_NAME
import net.jonasmf.auctionengine.repository.dynamodb.AUCTION_HOUSE_UPDATE_LOG_TABLE_NAME
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Role
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.dynamodb.model.TableStatus

@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class DynamoDBConfig {
    private val log = LoggerFactory.getLogger(DynamoDBConfig::class.java)
    private val tables =
        listOf(
            AuctionHouseDynamo.createTableRequest(),
            AuctionHouseUpdateLogDynamo.createTableRequest(),
        )
    private val tableNames =
        listOf<String>(
            AUCTION_HOUSE_TABLE_NAME,
            AUCTION_HOUSE_UPDATE_LOG_TABLE_NAME,
        )
    private val tableNamesJoined = tableNames.joinToString(", ")

    @Value("\${spring.cloud.aws.dynamodb.endpoint:}")
    private val amazonDynamoDBEndpoint: String? = null

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    fun dynamoDbTableNameResolver(): DynamoDbTableNameResolver {
        val defaultResolver = DefaultDynamoDbTableNameResolver()
        return object : DynamoDbTableNameResolver {
            override fun <T : Any?> resolve(clazz: Class<T>): String =
                when (clazz) {
                    AuctionHouseUpdateLogDynamo::class.java -> AUCTION_HOUSE_UPDATE_LOG_TABLE_NAME
                    AuctionHouseDynamo::class.java -> AUCTION_HOUSE_TABLE_NAME
                    else -> defaultResolver.resolve(clazz)
                }
        }
    }

    @Bean
    @Profile("!production")
    fun dynamoDbTableInitializer(dynamoDbClient: DynamoDbClient): ApplicationRunner =
        ApplicationRunner {
            val endpoint = amazonDynamoDBEndpoint?.trim().orEmpty()
            if (endpoint.isEmpty()) {
                log.info("Skipping DynamoDB local table bootstrap because no local endpoint is configured")
                return@ApplicationRunner
            }

            try {
                runBlocking {
                    createTableIfMissing(dynamoDbClient)
                    waitUntilActive(dynamoDbClient)
                }
                log.info("DynamoDB tables {} is active at {}", tableNamesJoined, endpoint)
            } catch (exception: Exception) {
                log.error(
                    "Failed to initialize DynamoDB tables {} at {}",
                    tableNamesJoined,
                    endpoint,
                    exception,
                )
                throw exception
            }
        }

    private suspend fun createTableIfMissing(dynamoDbClient: DynamoDbClient) {
        val createdTables = mutableListOf<String>()
        val existingTables = mutableListOf<String>()

        tables.forEach { table ->
            try {
                dynamoDbClient.createTable(table)
                createdTables += table.tableName()
            } catch (_: ResourceInUseException) {
                existingTables += table.tableName()
            }
        }

        if (createdTables.isNotEmpty()) {
            log.info("Created DynamoDB tables {} at {}", createdTables.joinToString(", "), amazonDynamoDBEndpoint)
        }
        if (existingTables.isNotEmpty()) {
            log.info(
                "DynamoDB tables {} already exists at {}",
                existingTables.joinToString(", "),
                amazonDynamoDBEndpoint,
            )
        }
    }

    private suspend fun waitUntilActive(dynamoDbClient: DynamoDbClient) {
        repeat(20) {
            try {
                val tableStatus =
                    tableNames.map {
                        dynamoDbClient
                            .describeTable(
                                DescribeTableRequest
                                    .builder()
                                    .tableName(it)
                                    .build(),
                            ).table()
                            ?.tableStatus()
                    }

                if (tableStatus.all { it == TableStatus.ACTIVE }) {
                    return
                }
            } catch (_: ResourceNotFoundException) {
                // Creation may still be propagating in local environments.
            }
            delay(500)
        }

        error("Timed out waiting for DynamoDB table $AUCTION_HOUSE_TABLE_NAME to become active")
    }
}
