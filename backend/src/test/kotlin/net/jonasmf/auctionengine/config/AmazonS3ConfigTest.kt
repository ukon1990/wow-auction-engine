package net.jonasmf.auctionengine.config

import aws.sdk.kotlin.services.s3.S3Client
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class AmazonS3ConfigTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(AmazonS3Config::class.java, TestConfiguration::class.java)
            .withPropertyValues(
                "spring.cloud.aws.credentials.access-key=test",
                "spring.cloud.aws.credentials.secret-key=test",
                "spring.cloud.aws.region.static=eu-west-1",
            )

    @Test
    fun `should use real aws s3 defaults when no endpoint override is configured`() {
        contextRunner.run { context ->
            val client = context.getBean(S3Client::class.java)

            assertEquals("eu-west-1", client.config.region)
            assertNull(client.config.endpointUrl)
            assertFalse(client.config.forcePathStyle)
        }
    }

    @Configuration
    class TestConfiguration {
        @Bean
        fun s3Properties() =
            WaeS3Properties(
                buckets =
                    mapOf(
                        "europe" to BucketConfig("wah-data-eu", "eu-west-1"),
                        "northamerica" to BucketConfig("wah-data-us", "us-west-1"),
                        "korea" to BucketConfig("wah-data-as", "ap-northeast-2"),
                        "taiwan" to BucketConfig("wah-data-as", "ap-northeast-2"),
                    ),
            )
    }
}
