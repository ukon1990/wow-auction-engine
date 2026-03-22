package net.jonasmf.auctionengine

import net.jonasmf.auctionengine.config.StubAuthWebClientConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@Import(TestcontainersConfiguration::class, StubAuthWebClientConfig::class)
@SpringBootTest
@ActiveProfiles("test")
class AuctionDTOEngineApplicationTests {
    companion object {
        private const val dynamoDbPort = 8000

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamoDbProperties(registry: DynamicPropertyRegistry) {
            val dynamoDb = TestcontainersConfiguration.dynamoDbContainer
            if (!dynamoDb.isRunning) {
                dynamoDb.start()
            }

            registry.add("amazon.dynamodb.endpoint") {
                "http://${dynamoDb.host}:${dynamoDb.getMappedPort(dynamoDbPort)}"
            }
            registry.add("amazon.aws.accesskey") { "fakeMyKeyId" }
            registry.add("amazon.aws.secretkey") { "fakeSecretAccessKey" }
        }
    }

    @Test
    fun contextLoads() {
    }
}
