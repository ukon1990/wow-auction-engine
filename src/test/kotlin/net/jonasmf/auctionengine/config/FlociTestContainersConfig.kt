package net.jonasmf.auctionengine.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer

@TestConfiguration(proxyBeanMethods = false)
class FlociTestContainersConfig {
    @Bean(destroyMethod = "")
    fun flociContainer(): GenericContainer<*> = FlociIntegrationTestBase.flociContainer
}
