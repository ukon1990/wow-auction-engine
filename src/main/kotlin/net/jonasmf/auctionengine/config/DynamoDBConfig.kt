package net.jonasmf.auctionengine.config

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.amazonaws.services.dynamodbv2.util.TableUtils
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import org.slf4j.LoggerFactory
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableDynamoDBRepositories(basePackages = ["net.jonasmf.auctionengine.repository.dynamodb"])
class DynamoDBConfig {
    private val log = LoggerFactory.getLogger(DynamoDBConfig::class.java)

    @Value("\${amazon.dynamodb.endpoint}")
    private val amazonDynamoDBEndpoint: String? = null

    @Value("\${amazon.aws.accesskey}")
    private val amazonAWSAccessKey: String? = null

    @Value("\${amazon.aws.secretkey}")
    private val amazonAWSSecretKey: String? = null

    @Bean
    fun amazonDynamoDB(): AmazonDynamoDB {
        val builder =
            AmazonDynamoDBClient
                .builder()
                .withCredentials(AWSStaticCredentialsProvider(amazonAWSCredentials()))
        if (!amazonDynamoDBEndpoint.isNullOrEmpty()) {
            builder.withEndpointConfiguration(
                AwsClientBuilder.EndpointConfiguration(
                    amazonDynamoDBEndpoint,
                    "eu-west-1",
                ),
            )
        } else {
            builder.withRegion("eu-west-1")
        }
        return builder.build()
    }

    @Bean
    fun amazonAWSCredentials(): AWSCredentials =
        BasicAWSCredentials(
            amazonAWSAccessKey,
            amazonAWSSecretKey,
        )

    @Bean
    @Profile("!production")
    fun dynamoDbTableInitializer(amazonDynamoDB: AmazonDynamoDB): ApplicationRunner =
        ApplicationRunner {
            val endpoint = amazonDynamoDBEndpoint?.trim().orEmpty()
            if (endpoint.isEmpty()) {
                log.info("Skipping DynamoDB local table bootstrap because no local endpoint is configured")
                return@ApplicationRunner
            }

            val mapper = DynamoDBMapper(amazonDynamoDB)
            val createTableRequest =
                mapper
                    .generateCreateTableRequest(AuctionHouseDynamo::class.java)
                    .withProvisionedThroughput(ProvisionedThroughput(5L, 5L))

            try {
                val created = TableUtils.createTableIfNotExists(amazonDynamoDB, createTableRequest)
                if (created) {
                    log.info("Created DynamoDB table {} at {}", createTableRequest.tableName, endpoint)
                } else {
                    log.info("DynamoDB table {} already exists at {}", createTableRequest.tableName, endpoint)
                }

                TableUtils.waitUntilActive(amazonDynamoDB, createTableRequest.tableName)
                log.info("DynamoDB table {} is active at {}", createTableRequest.tableName, endpoint)
            } catch (exception: Exception) {
                log.error(
                    "Failed to initialize DynamoDB table {} at {}",
                    createTableRequest.tableName,
                    endpoint,
                    exception,
                )
                throw exception
            }
        }
}
