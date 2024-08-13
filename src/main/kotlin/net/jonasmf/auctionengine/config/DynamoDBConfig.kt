package net.jonasmf.auctionengine.config

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableDynamoDBRepositories(basePackages = ["net.jonasmf.auctionengine.repositories.dynamodb"])
class DynamoDBConfig {
    @Value("\${amazon.dynamodb.endpoint}")
    private val amazonDynamoDBEndpoint: String? = null

    @Value("\${amazon.aws.accesskey}")
    private val amazonAWSAccessKey: String? = null

    @Value("\${amazon.aws.secretkey}")
    private val amazonAWSSecretKey: String? = null

    @Bean
    fun amazonDynamoDB(): AmazonDynamoDB {
        val builder = AmazonDynamoDBClient.builder()
            .withCredentials(AWSStaticCredentialsProvider(amazonAWSCredentials()))
        if (!amazonDynamoDBEndpoint.isNullOrEmpty()) {
            builder.withEndpointConfiguration(
                AwsClientBuilder.EndpointConfiguration(
                    amazonDynamoDBEndpoint,
                    "eu-west-1"
                )
            )
        } else {
            builder.withRegion("eu-west-1")
        }
        return builder.build()
    }

    @Bean
    fun amazonAWSCredentials(): AWSCredentials {
        return BasicAWSCredentials(
            amazonAWSAccessKey, amazonAWSSecretKey
        )
    }
}