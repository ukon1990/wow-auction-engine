package net.jonasmf.auctionengine

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    companion object {
        private const val dynamoDbPort = 8000

        @JvmField
        val dynamoDbContainer: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("amazon/dynamodb-local:latest"))
                .withExposedPorts(dynamoDbPort)
                .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
    }

    @Bean
    @ServiceConnection
    fun mariaDbContainer(): MariaDBContainer<*> = MariaDBContainer(DockerImageName.parse("mariadb:latest"))

    @Bean(destroyMethod = "")
    fun dynamoDbContainer(): GenericContainer<*> = dynamoDbContainer
}
