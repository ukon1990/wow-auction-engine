package net.jonasmf.auctionengine.config

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.BucketAlreadyExists
import aws.sdk.kotlin.services.s3.model.BucketAlreadyOwnedByYou
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.sdk.kotlin.services.s3.model.ListBucketsRequest
import aws.sdk.kotlin.services.s3.model.S3Exception
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking
import net.jonasmf.auctionengine.utility.supportedBucketNames
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Role

@Configuration
class AmazonS3Config {
    private val log = LoggerFactory.getLogger(AmazonS3Config::class.java)

    @Value("\${spring.cloud.aws.credentials.access-key:}")
    private var awsAccessKeyId: String = ""

    @Value("\${spring.cloud.aws.credentials.secret-key:}")
    private var awsSecretKey: String = ""

    @Value("\${spring.cloud.aws.region.static}")
    private lateinit var region: String

    @Value("\${spring.cloud.aws.s3.endpoint:}")
    private lateinit var s3Endpoint: String

    @Value("\${spring.cloud.aws.s3.bootstrap-enabled:false}")
    private var bootstrapEnabled: Boolean = false

    @Bean
    fun amazonS3(): S3Client =
        S3Client {
            this.region = this@AmazonS3Config.region
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

    @Bean
    @Profile("!production")
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    fun s3BucketInitializer(amazonS3: S3Client): ApplicationRunner =
        ApplicationRunner {
            val endpoint = s3Endpoint.trim()
            if (!bootstrapEnabled || endpoint.isEmpty()) {
                log.info("Skipping S3 local bucket bootstrap because no local endpoint is configured")
                return@ApplicationRunner
            }

            runBlocking {
                val existingBuckets =
                    try {
                        amazonS3
                            .listBuckets(ListBucketsRequest {})
                            .buckets
                            ?.mapNotNull { it.name }
                            ?.toSet()
                            .orEmpty()
                    } catch (exception: Exception) {
                        log.warn(
                            "Failed to list S3 buckets at {}. Continuing with best-effort local bucket bootstrap.",
                            endpoint,
                            exception,
                        )
                        return@runBlocking
                    }

                supportedBucketNames.forEach { bucket ->
                    if (bucket in existingBuckets) {
                        log.info("S3 bucket {} already exists at {}", bucket, endpoint)
                        return@forEach
                    }

                    try {
                        amazonS3.createBucket(CreateBucketRequest { this.bucket = bucket })
                        log.info("Created S3 bucket {} at {}", bucket, endpoint)
                    } catch (_: BucketAlreadyOwnedByYou) {
                        log.info("S3 bucket {} already exists at {}", bucket, endpoint)
                    } catch (_: BucketAlreadyExists) {
                        log.info("S3 bucket {} already exists at {}", bucket, endpoint)
                    } catch (exception: S3Exception) {
                        log.warn(
                            "Failed to create S3 bucket {} at {}. Continuing startup because this is local-only bootstrap.",
                            bucket,
                            endpoint,
                            exception,
                        )
                    }
                }
            }
        }
}
