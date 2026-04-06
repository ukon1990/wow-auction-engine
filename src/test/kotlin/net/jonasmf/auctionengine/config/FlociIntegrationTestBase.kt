package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.testsupport.container.SharedTestContainers
import org.springframework.test.context.DynamicPropertyRegistry

abstract class FlociIntegrationTestBase : IntegrationTestBase() {
    companion object {
        @JvmField
        val flociContainer = SharedTestContainers.flociContainer

        @JvmStatic
        fun startFloci() {
            SharedTestContainers.startFloci()
        }

        @JvmStatic
        fun registerFlociCredentials(registry: DynamicPropertyRegistry) {
            SharedTestContainers.registerFlociCredentials(registry)
        }
    }
}
