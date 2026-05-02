package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.testsupport.container.SharedTestContainers
import net.jonasmf.auctionengine.testsupport.database.TestDataCleaner
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
@ActiveProfiles("test")
@Import(StubAuthWebClientConfig::class)
abstract class IntegrationTestBase {
    @Autowired
    lateinit var testDataCleaner: TestDataCleaner

    @BeforeEach
    fun cleanRelationalDatabaseBeforeEach() {
        testDataCleaner.resetRelationalDatabase()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerMariaDbProperties(registry: DynamicPropertyRegistry) {
            SharedTestContainers.registerMariaDbProperties(registry)
        }
    }
}
