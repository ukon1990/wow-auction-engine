package net.jonasmf.auctionengine.config

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

abstract class S3IntegrationTestBase : FlociIntegrationTestBase() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerS3Properties(registry: DynamicPropertyRegistry) {
            registerFlociCredentials(registry)
            registry.add("spring.cloud.aws.s3.endpoint") {
                "http://${flociContainer.host}:${flociContainer.getMappedPort(4566)}"
            }
            registry.add("spring.cloud.aws.s3.bootstrap-enabled") { true }
        }
    }
}
