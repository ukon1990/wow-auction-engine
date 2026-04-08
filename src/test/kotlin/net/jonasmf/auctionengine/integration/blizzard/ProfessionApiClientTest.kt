package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Test

class ProfessionApiClientTest {
    @Test
    fun getAll() {
    }

    @Test
    fun getById() {
    }

    private fun professionIndexBody(): String = loadFixture("blizzard/profession/index-response.json")
}
