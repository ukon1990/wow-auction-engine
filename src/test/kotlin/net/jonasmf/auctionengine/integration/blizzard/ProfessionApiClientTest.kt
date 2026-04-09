package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Test

class ProfessionApiClientTest {
    @Test
    fun getAll() {
        // TODO
    }

    @Test
    fun getById() {
        // TODO
    }

    private fun professionIndexBody(): String = loadFixture(this, "blizzard/profession/index-response.json")

    private fun professionById(id: Int): String = loadFixture(this, "blizzard/profesion/details/$id-response.json")

    private fun professionSkillTierById(id: Int): String =
        loadFixture(this, "blizzard/profesion/skill-tier/$id-response.json")
}
