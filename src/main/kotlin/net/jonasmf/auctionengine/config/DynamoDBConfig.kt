package net.jonasmf.auctionengine.config

import io.awspring.cloud.dynamodb.DefaultDynamoDbTableNameResolver
import io.awspring.cloud.dynamodb.DynamoDbTableNameResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.repository.dynamodb.AUCTION_HOUSE_TABLE_NAME
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

    @Value("\${spring.cloud.aws.dynamodb.endpoint:}")
    private val amazonDynamoDBEndpoint: String? = null

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    fun dynamoDbTableNameResolver(): DynamoDbTableNameResolver {
        val defaultResolver = DefaultDynamoDbTableNameResolver()
        return object : DynamoDbTableNameResolver {
            override fun <T : Any?> resolve(clazz: Class<T>): String =
                if (clazz == AuctionHouseDynamo::class.java) {
                    AUCTION_HOUSE_TABLE_NAME
                } else {
                    defaultResolver.resolve(clazz)
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
                log.info("DynamoDB table {} is active at {}", AUCTION_HOUSE_TABLE_NAME, endpoint)
            } catch (exception: Exception) {
                log.error(
                    "Failed to initialize DynamoDB table {} at {}",
                    AUCTION_HOUSE_TABLE_NAME,
                    endpoint,
                    exception,
                )
                throw exception
            }
        }

    private suspend fun createTableIfMissing(dynamoDbClient: DynamoDbClient) {
        try {
            val tables =
                listOf( // The plan is to have more soon
                    AuctionHouseDynamo.createTableRequest(),
                )
            tables.forEach { dynamoDbClient.createTable(it) }
            log.info("Created DynamoDB table {} at {}", AUCTION_HOUSE_TABLE_NAME, amazonDynamoDBEndpoint)
        } catch (_: ResourceInUseException) {
            log.info("DynamoDB table {} already exists at {}", AUCTION_HOUSE_TABLE_NAME, amazonDynamoDBEndpoint)
        }
    }

    private suspend fun waitUntilActive(dynamoDbClient: DynamoDbClient) {
        repeat(20) {
            try {
                val tableStatus =
                    dynamoDbClient
                        .describeTable(
                            DescribeTableRequest
                                .builder()
                                .tableName(AUCTION_HOUSE_TABLE_NAME)
                                .build(),
                        ).table()
                        ?.tableStatus()

                if (tableStatus == TableStatus.ACTIVE) {
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
