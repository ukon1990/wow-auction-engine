package net.jonasmf.auctionengine.controller

import io.mockk.every
import io.mockk.mockk
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.service.CommunityRealms
import net.jonasmf.auctionengine.service.RealmQueryService
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.jonasmf.auctionengine.generated.model.Realm as RealmDto

class RealmControllerTest {
    private val connectedRealmRepository = mockk<ConnectedRealmRepository>()
    private val realmQueryService = RealmQueryService(connectedRealmRepository)
    private val controller = RealmController(realmQueryService)

    private val euRegion = RegionDBO(id = 2, name = "EU", type = Region.Europe)
    private val usRegion = RegionDBO(id = 1, name = "US", type = Region.NorthAmerica)

    @Test
    fun `listRealms returns mapped realms sorted by region then name and excludes community placeholders`() {
        val euConnected =
            connectedRealm(
                id = 1234,
                realms =
                    listOf(
                        realm(id = 1, region = euRegion, name = "Stormrage", slug = "stormrage", locale = Locale.EN_GB),
                        realm(
                            id = 2,
                            region = euRegion,
                            name = "Aerie Peak",
                            slug = "aerie-peak",
                            locale = Locale.EN_GB,
                        ),
                    ),
            )
        val usConnected =
            connectedRealm(
                id = 5678,
                realms =
                    listOf(
                        realm(id = 3, region = usRegion, name = "Illidan", slug = "illidan", locale = Locale.EN_US),
                    ),
            )
        val communityEu =
            connectedRealm(
                id = CommunityRealms.idFor(Region.Europe),
                realms =
                    listOf(
                        realm(
                            id = -2,
                            region = euRegion,
                            name = "Community",
                            slug = "community",
                            locale = Locale.EN_GB,
                            category = "Community",
                        ),
                    ),
            )
        every { connectedRealmRepository.findAll() } returns listOf(euConnected, usConnected, communityEu)

        val response = controller.listRealms()

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(
            listOf("eu" to "Aerie Peak", "eu" to "Stormrage", "us" to "Illidan"),
            body.map {
                it.region.value to
                    it.name
            },
        )
        assertEquals(RealmDto.Region.EU, body.first().region)
        assertEquals("aerie-peak", body.first().slug)
        assertEquals("en_GB", body.first().locale)
    }

    @Test
    fun `getRealm returns mapped detail with connected and community auction house status`() {
        val lastModified = Instant.parse("2026-05-01T10:00:00Z")
        val nextUpdate = Instant.parse("2026-05-01T11:00:00Z")
        val lastDailyPrice = Instant.parse("2026-05-01T05:00:00Z")
        val realm = realm(id = 10, region = euRegion, name = "Stormrage", slug = "stormrage", locale = Locale.EN_GB)
        val connected =
            connectedRealm(
                id = 1234,
                realms = listOf(realm),
                lastModified = lastModified,
                nextUpdate = nextUpdate,
                lastDailyPriceUpdate = lastDailyPrice,
            )
        val communityModified = Instant.parse("2026-05-01T09:00:00Z")
        val community =
            connectedRealm(
                id = CommunityRealms.idFor(Region.Europe),
                realms =
                    listOf(
                        realm(
                            id = -2,
                            region = euRegion,
                            name = "Community",
                            slug = "community",
                            locale = Locale.EN_GB,
                            category = "Community",
                        ),
                    ),
                lastModified = communityModified,
            )
        every { connectedRealmRepository.findAllByRegion(Region.Europe) } returns listOf(connected)
        every { connectedRealmRepository.findById(CommunityRealms.idFor(Region.Europe)) } returns Optional.of(community)

        val response = controller.getRealm("eu", "Stormrage")

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("stormrage", body.realm.slug)
        assertEquals(RealmDto.Region.EU, body.realm.region)
        assertEquals(1234, body.auctionHouse.connectedRealmId)
        assertEquals(lastModified, body.auctionHouse.lastModified?.toInstant())
        assertEquals(nextUpdate, body.auctionHouse.nextUpdate?.toInstant())
        assertEquals(lastDailyPrice, body.auctionHouse.lastDailyPriceUpdate?.toInstant())
        assertEquals(CommunityRealms.idFor(Region.Europe), body.community.connectedRealmId)
        assertEquals(communityModified, body.community.lastModified?.toInstant())
        assertNull(body.community.lastDailyPriceUpdate)
    }

    @Test
    fun `getRealm returns 404 when no realm matches the region and slug`() {
        every { connectedRealmRepository.findAllByRegion(Region.Europe) } returns emptyList()

        val response = controller.getRealm("eu", "ghost-realm")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `getRealm returns 404 when the community sibling is missing for the region`() {
        val realm = realm(id = 10, region = euRegion, name = "Stormrage", slug = "stormrage", locale = Locale.EN_GB)
        val connected = connectedRealm(id = 1234, realms = listOf(realm))
        every { connectedRealmRepository.findAllByRegion(Region.Europe) } returns listOf(connected)
        every { connectedRealmRepository.findById(CommunityRealms.idFor(Region.Europe)) } returns Optional.empty()

        val response = controller.getRealm("eu", "stormrage")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `getRealm returns 404 when the region path parameter is unknown`() {
        val response = controller.getRealm("zz", "stormrage")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    private fun realm(
        id: Int,
        region: RegionDBO,
        name: String,
        slug: String,
        locale: Locale,
        category: String = "Player vs. Player",
        timezone: String = "Europe/Paris",
    ): Realm =
        Realm(
            id = id,
            region = region,
            name = name,
            category = category,
            locale = locale,
            timezone = timezone,
            gameBuild = GameBuildVersion.RETAIL,
            slug = slug,
        )

    private fun connectedRealm(
        id: Int,
        realms: List<Realm>,
        lastModified: Instant? = null,
        nextUpdate: Instant? = null,
        lastDailyPriceUpdate: Instant? = null,
    ): ConnectedRealm {
        val regionType = realms.first().region.type
        val auctionHouse =
            AuctionHouse(
                connectedId = id,
                region = regionType,
                lastModified = lastModified,
                nextUpdate = nextUpdate,
                lastDailyPriceUpdate = lastDailyPriceUpdate,
            )
        return ConnectedRealm(
            id = id,
            auctionHouse = auctionHouse,
            realms = realms.toMutableList(),
        )
    }
}
