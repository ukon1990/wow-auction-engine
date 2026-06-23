package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.config.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AdminStatusServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var adminStatusService: AdminStatusService

    @Test
    fun `table size query is valid for MariaDB`() {
        val tableSizes = adminStatusService.getTableSizes()

        assertTrue(tableSizes.any { it.name == "auction" })
    }
}
