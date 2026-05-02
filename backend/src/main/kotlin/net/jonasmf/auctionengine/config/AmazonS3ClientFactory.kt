package net.jonasmf.auctionengine.config

import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.net.url.Url
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class AmazonS3ClientFactory(
    @Value("\${spring.cloud.aws.credentials.access-key:}")
    private val awsAccessKeyId: String,
    @Value("\${spring.cloud.aws.credentials.secret-key:}")
    private val awsSecretKey: String,
    @Value("\${spring.cloud.aws.s3.endpoint:}")
    private val s3Endpoint: String,
) {
    private val clients = ConcurrentHashMap<String, S3Client>()

    fun create(region: String): S3Client =
        clients.computeIfAbsent(region) {
            S3Client {
                this.region = region
                if (awsAccessKeyId.isNotBlank() && awsSecretKey.isNotBlank()) {
                    credentialsProvider =
                        staticCredentialsProvider(
                            accessKeyId = awsAccessKeyId,
                            secretAccessKey = awsSecretKey,
                        )
                }
                val endpoint = s3Endpoint.trim()
                if (endpoint.isNotEmpty()) {
                    endpointUrl = Url.parse(endpoint)
                    forcePathStyle = true
                }
            }
        }

    @PreDestroy
    fun close() {
        clients.values.forEach { it.close() }
    }
}
