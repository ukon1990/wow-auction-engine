package net.jonasmf.auctionengine.config

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(MariaDBTestcontainersConfig::class, StubAuthWebClientConfig::class)
abstract class IntegrationTestBase

