package net.jonasmf.auctionengine.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class MariaDBTestcontainersConfig {
    @Bean
    @ServiceConnection
    fun mariaDbContainer(): MariaDBContainer<*> = MariaDBContainer(DockerImageName.parse("mariadb:latest"))
}
