package net.jonasmf.auctionengine

import net.jonasmf.auctionengine.config.StubAuthWebClientConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer

@Import(TestcontainersConfiguration::class, StubAuthWebClientConfig::class)
@SpringBootTest
@ActiveProfiles("test")
class AuctionDTOEngineApplicationTests {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerDynamoDbProperties(registry: DynamicPropertyRegistry) {
            val localStack = TestcontainersConfiguration.localStackContainer
            if (!localStack.isRunning) {
                localStack.start()
            }

            registry.add("amazon.dynamodb.endpoint") {
                localStack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString()
            }
            registry.add("amazon.aws.accesskey") { localStack.accessKey }
            registry.add("amazon.aws.secretkey") { localStack.secretKey }
        }
    }

    @Test
    fun contextLoads() {
    }
}
