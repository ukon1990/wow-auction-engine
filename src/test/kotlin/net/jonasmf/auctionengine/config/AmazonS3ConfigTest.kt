package net.jonasmf.auctionengine.config

import aws.sdk.kotlin.services.s3.S3Client
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class AmazonS3ConfigTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(AmazonS3Config::class.java)
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
}
