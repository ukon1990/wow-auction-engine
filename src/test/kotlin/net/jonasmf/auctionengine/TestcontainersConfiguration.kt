package net.jonasmf.auctionengine

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    companion object {
        @JvmField
        val localStackContainer: LocalStackContainer =
            LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
                .withServices(LocalStackContainer.Service.DYNAMODB)
    }

    @Bean
    @ServiceConnection
    fun mariaDbContainer(): MariaDBContainer<*> = MariaDBContainer(DockerImageName.parse("mariadb:latest"))

    @Bean
    fun localStackContainer(): LocalStackContainer = localStackContainer
}
