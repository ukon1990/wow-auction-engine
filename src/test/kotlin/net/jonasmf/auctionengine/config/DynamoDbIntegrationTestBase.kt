package net.jonasmf.auctionengine.config

import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

@Import(DynamoDBTestContainersConfig::class)
abstract class DynamoDbIntegrationTestBase : IntegrationTestBase() {
    companion object {
        @JvmField
        val localStackContainer: LocalStackContainer =
            LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
                .withServices(LocalStackContainer.Service.DYNAMODB)

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamoDbProperties(registry: DynamicPropertyRegistry) {
            val localStack = localStackContainer
            if (!localStack.isRunning) {
                localStack.start()
            }

            registry.add("spring.cloud.aws.dynamodb.endpoint") {
                localStack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString()
            }
            registry.add("spring.cloud.aws.credentials.access-key") { localStack.accessKey }
            registry.add("spring.cloud.aws.credentials.secret-key") { localStack.secretKey }
        }
    }
}
