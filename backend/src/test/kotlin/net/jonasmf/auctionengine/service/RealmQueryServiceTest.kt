package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.mockk
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.generated.model.Realm
import net.jonasmf.auctionengine.repository.rds.RealmCatalogJdbcRepository
import net.jonasmf.auctionengine.repository.rds.RealmCatalogRow
import net.jonasmf.auctionengine.repository.rds.RealmDetailRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class RealmQueryServiceTest {
    private val repository = mockk<RealmCatalogJdbcRepository>()
    private val service = RealmQueryService(repository)

    @Test
    fun `listAllRealms maps and sorts catalog rows`() {
        every { repository.findRealmCatalogRows() } returns
            listOf(
                RealmCatalogRow(
                    regionId = 2,
                    name = "Stormrage",
                    category = "PvP",
                    slug = "stormrage",
                    locale = "en_GB",
                    timezone = "Europe/Paris",
                ),
                RealmCatalogRow(
                    regionId = 1,
                    name = "Illidan",
                    category = "PvE",
                    slug = "illidan",
                    locale = "en_US",
                    timezone = "America/Chicago",
                ),
            )

        val result = service.listAllRealms()

        assertEquals(listOf("Stormrage", "Illidan"), result.realms.map { it.name })
        assertEquals(Realm.Region.EU, result.realms.first().region)
    }

    @Test
    fun `getRealmDetail maps detail rows`() {
        every { repository.findRealmDetailRow(Region.Europe, "stormrage") } returns
            RealmDetailRow(
                connectedRealmId = 1234,
                regionId = 2,
                name = "Stormrage",
                category = "PvP",
                slug = "stormrage",
                locale = "en_GB",
                timezone = "Europe/Paris",
                lastDailyPriceUpdate = Instant.parse("2026-05-01T05:00:00Z"),
                lastModified = Instant.parse("2026-05-01T10:00:00Z"),
                nextUpdate = Instant.parse("2026-05-01T11:00:00Z"),
                commodityConnectedRealmId = -2,
                commodityLastDailyPriceUpdate = null,
                commodityLastModified = Instant.parse("2026-05-01T09:00:00Z"),
                commodityNextUpdate = null,
            )

        val detail = service.getRealmDetail("eu", "stormrage")

        assertEquals("stormrage", detail?.realm?.slug)
        assertEquals(1234, detail?.auctionHouse?.connectedRealmId)
        assertEquals(-2, detail?.commodity?.connectedRealmId)
    }

    @Test
    fun `getRealmDetail returns null for unknown region or missing row`() {
        every { repository.findRealmDetailRow(Region.Europe, "ghost") } returns null

        assertNull(service.getRealmDetail("zz", "stormrage"))
        assertNull(service.getRealmDetail("eu", "ghost"))
    }
}
