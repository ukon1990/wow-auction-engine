package net.jonasmf.auctionengine.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.localstack.LocalStackContainer

@TestConfiguration(proxyBeanMethods = false)
class DynamoDBTestContainersConfig {

    @Bean(destroyMethod = "")
    fun localStackContainer(): LocalStackContainer = DynamoDbIntegrationTestBase.localStackContainer
}
