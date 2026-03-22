package net.jonasmf.auctionengine.config

import aws.sdk.kotlin.services.s3.S3Client
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AmazonS3Config {
    @Value("\${spring.cloud.aws.credentials.access-key}")
    private lateinit var awsAccessKeyId: String

    @Value("\${spring.cloud.aws.credentials.secret-key}")
    private lateinit var awsSecretKey: String

    @Value("\${spring.cloud.aws.region.static}")
    private lateinit var region: String

    @Bean
    fun amazonS3(): S3Client =
        S3Client {
            this.region = region
            credentialsProvider =
                staticCredentialsProvider(
                    accessKeyId = awsAccessKeyId,
                    secretAccessKey = awsSecretKey,
                )
        }
}
