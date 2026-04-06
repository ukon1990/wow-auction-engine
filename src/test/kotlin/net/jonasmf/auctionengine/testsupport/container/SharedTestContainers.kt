package net.jonasmf.auctionengine.testsupport.container

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

object SharedTestContainers {
    private val mariaDbImage = DockerImageName.parse("mariadb:11.4.10")
    private val flociImage = DockerImageName.parse("hectorvent/floci:1.0.8")

    @JvmField
    val mariaDbContainer: MariaDBContainer<*> =
        MariaDBContainer(mariaDbImage)
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")

    @JvmField
    val flociContainer: GenericContainer<*> =
        GenericContainer(flociImage)
            .withExposedPorts(4566)
            .withEnv("FLOCI_STORAGE_MODE", "memory")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(30))

    @JvmStatic
    fun startMariaDb() {
        if (!mariaDbContainer.isRunning) {
            mariaDbContainer.start()
        }
    }

    @JvmStatic
    fun startFloci() {
        if (!flociContainer.isRunning) {
            flociContainer.start()
        }
    }

    @JvmStatic
    fun registerMariaDbProperties(registry: DynamicPropertyRegistry) {
        startMariaDb()
        registry.add("spring.datasource.url") { mariaDbContainer.jdbcUrl }
        registry.add("spring.datasource.username") { mariaDbContainer.username }
        registry.add("spring.datasource.password") { mariaDbContainer.password }
        registry.add("spring.datasource.driver-class-name") { mariaDbContainer.driverClassName }
    }

    @JvmStatic
    fun registerFlociCredentials(registry: DynamicPropertyRegistry) {
        startFloci()
        registry.add("spring.cloud.aws.credentials.access-key") { "test" }
        registry.add("spring.cloud.aws.credentials.secret-key") { "test" }
    }

    @JvmStatic
    fun configureSystemProperties() {
        startMariaDb()
        System.setProperty("spring.datasource.url", mariaDbContainer.jdbcUrl)
        System.setProperty("spring.datasource.username", mariaDbContainer.username)
        System.setProperty("spring.datasource.password", mariaDbContainer.password)
        System.setProperty("spring.datasource.driver-class-name", mariaDbContainer.driverClassName)
    }
}
