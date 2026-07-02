package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.integration.blizzard.BlizzardApiSupport
import net.jonasmf.auctionengine.interceptor.authHeaderFilterFunction
import net.jonasmf.auctionengine.service.AuthService
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.buildFilteredWebClient
import net.jonasmf.auctionengine.testsupport.BlizzardFixtures
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Keeps Spring integration tests from opening real Blizzard HTTP connections.
 *
 * Add or refresh files under src/test/resources/blizzard when a test needs another endpoint.
 */
@TestConfiguration(proxyBeanMethods = false)
class BlizzardFixtureIntegrationConfig {
    @Bean
    @Primary
    fun blizzardApiSupport(
        blizzardApiProperties: BlizzardApiProperties,
        authService: AuthService,
    ): BlizzardApiSupport =
        BlizzardApiSupport(
            properties = blizzardApiProperties,
            webClientWithAuth =
                buildFilteredWebClient(
                    BlizzardFixtures::handleRequest,
                    authHeaderFilterFunction(authService, blizzardApiProperties),
                ),
        )
}
