package net.jonasmf.auctionengine.config

import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@Import(FlociTestContainersConfig::class)
abstract class FlociIntegrationTestBase : IntegrationTestBase() {
    companion object {
        private val flociImage = DockerImageName.parse("hectorvent/floci:latest")

        @JvmField
        val flociContainer: GenericContainer<*> =
            GenericContainer(flociImage)
                .withExposedPorts(4566)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(30))

        @JvmStatic
        fun startFloci() {
            if (!flociContainer.isRunning) {
                flociContainer.start()
            }
        }

        @JvmStatic
        fun registerFlociCredentials(registry: DynamicPropertyRegistry) {
            startFloci()
            registry.add("spring.cloud.aws.credentials.access-key") { "test" }
            registry.add("spring.cloud.aws.credentials.secret-key") { "test" }
        }
    }
}
