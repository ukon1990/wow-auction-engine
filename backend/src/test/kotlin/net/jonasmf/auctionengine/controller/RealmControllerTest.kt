package net.jonasmf.auctionengine.controller

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.jonasmf.auctionengine.generated.model.AuctionHouseStatus
import net.jonasmf.auctionengine.generated.model.Realm
import net.jonasmf.auctionengine.generated.model.RealmDetail
import net.jonasmf.auctionengine.service.RealmCatalogResult
import net.jonasmf.auctionengine.service.RealmQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class RealmControllerTest {
    private val realmQueryService = mockk<RealmQueryService>()
    private val controller = RealmController(realmQueryService)

    @Test
    fun `listRealms returns catalog with server timing header`() {
        val catalog =
            RealmCatalogResult(
                realms =
                    listOf(
                        Realm(
                            region = Realm.Region.EU,
                            name = "Stormrage",
                            category = "PvP",
                            slug = "stormrage",
                            locale = "en_GB",
                            timezone = "Europe/Paris",
                        ),
                    ),
                queryDuration = 3.milliseconds,
                mapSortDuration = 2.milliseconds,
        )
        every { realmQueryService.listAllRealms() } returns catalog

        val response = runBlocking { controller.listRealms() }

        assertEquals(200, response.statusCode.value())
        assertEquals(listOf("Stormrage"), response.body?.map { it.name })
        assertNotNull(response.headers.getFirst("Server-Timing"))
    }

    @Test
    fun `getRealm forwards detail from the service`() {
        val detail =
            RealmDetail(
                realm =
                    Realm(
                        region = Realm.Region.EU,
                        name = "Stormrage",
                        category = "PvP",
                        slug = "stormrage",
                        locale = "en_GB",
                        timezone = "Europe/Paris",
                    ),
                auctionHouse =
                    AuctionHouseStatus(
                        connectedRealmId = 1234,
                        lastDailyPriceUpdate = null,
                        lastModified = null,
                        nextUpdate = null,
                    ),
                commodity =
                    AuctionHouseStatus(
                        connectedRealmId = -2,
                        lastDailyPriceUpdate = null,
                        lastModified = null,
                        nextUpdate = null,
                    ),
        )
        every { realmQueryService.getRealmDetail("eu", "stormrage") } returns detail

        val response = runBlocking { controller.getRealm("eu", "stormrage") }

        assertEquals(200, response.statusCode.value())
        assertEquals("stormrage", response.body?.realm?.slug)
    }
}
