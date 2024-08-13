package net.jonasmf.auctionengine.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AmazonS3Config {

    @Value("\${cloud.aws.credentials.access-key}")
    private lateinit var awsAccessKeyId: String

    @Value("\${cloud.aws.credentials.secret-key}")
    private lateinit var awsSecretKey: String

    @Value("\${cloud.aws.region.static}")
    private lateinit var region: String

    @Bean
    fun amazonS3(): AmazonS3 {
        val awsCreds = BasicAWSCredentials(awsAccessKeyId, awsSecretKey)
        return AmazonS3ClientBuilder.standard()
            .withRegion(region)
            .withCredentials(AWSStaticCredentialsProvider(awsCreds))
            .build()
    }
}