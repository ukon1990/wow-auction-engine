package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.buildWebClient
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.createSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlizzardApiSupportTest {
    @Test
    fun `buildRegionalUri does not duplicate base path when request path already includes data wow`() {
        val support = createSupport(buildWebClient { error("not used") })

        val uri =
            support.buildRegionalUri(
                region = Region.Europe,
                path = "/data/wow/profession/index",
                namespace = "static-eu",
                locale = "en_US",
            )

        assertEquals(
            "https://eu.api.blizzard.test/data/wow/profession/index?namespace=static-eu&locale=en_US",
            uri,
        )
    }

    @Test
    fun `buildRegionalUri keeps relative path when request path does not include base path`() {
        val support = createSupport(buildWebClient { error("not used") })

        val uri =
            support.buildRegionalUri(
                region = Region.Europe,
                path = "connected-realm/index",
                namespace = "dynamic-eu",
                locale = "en_GB",
            )

        assertEquals(
            "https://eu.api.blizzard.test/data/wow/connected-realm/index?namespace=dynamic-eu&locale=en_GB",
            uri,
        )
    }
}
