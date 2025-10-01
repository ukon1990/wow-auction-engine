package net.jonasmf.auctionengine

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun mariaDbContainer(): MariaDBContainer<*> = MariaDBContainer(DockerImageName.parse("mariadb:latest"))

    @Bean
    fun localStackContainer(): LocalStackContainer {
        val container = LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
        container.withServices(LocalStackContainer.Service.DYNAMODB)
        return container
    }
}
