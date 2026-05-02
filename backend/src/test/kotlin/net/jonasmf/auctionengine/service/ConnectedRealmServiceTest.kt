package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.integration.blizzard.BlizzardConnectedRealmApiClient
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.repository.rds.RegionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.Optional
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository as AuctionHouseEntityRepository

class ConnectedRealmServiceTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe),
        )

    @Test
    fun `getById repairs stale auction house link when authoritative connected id row exists`() {
        val staleAuctionHouse = AuctionHouse(id = 10, connectedId = 0, region = Region.Korea)
        val authoritativeAuctionHouse = AuctionHouse(id = 20, connectedId = 1604, region = Region.Europe)
        val connectedRealm =
            ConnectedRealm(
                id = 1604,
                auctionHouse = staleAuctionHouse,
                realms =
                    mutableListOf(
                        Realm(
                            id = 1604,
                            region = RegionDBO(id = 2, name = "Europe", type = Region.Europe),
                            name = "Realm 1604",
                            category = "Normal",
                            locale = Locale.EN_GB,
                            timezone = "UTC",
                            gameBuild = GameBuildVersion.RETAIL,
                            slug = "realm-1604",
                        ),
                    ),
            )

        val connectedRealmRepository = mockk<ConnectedRealmRepository>()
        val auctionHouseRepository = mockk<AuctionHouseEntityRepository>()
        every { connectedRealmRepository.findById(1604) } returns Optional.of(connectedRealm)
        every { auctionHouseRepository.findByConnectedId(1604) } returns Optional.of(authoritativeAuctionHouse)
        every { connectedRealmRepository.save(any()) } answers { firstArg() }

        val service =
            ConnectedRealmService(
                properties = properties,
                blizzardConnectedRealmApiClient = mockk<BlizzardConnectedRealmApiClient>(),
                regionService = mockk<RegionService>(),
                connectedRealmRepository = connectedRealmRepository,
                regionRepository = mockk<RegionRepository>(),
                auctionHouseService = mockk<AuctionHouseService>(),
                connectedRealmBulkSyncService = mockk<ConnectedRealmBulkSyncService>(),
                auctionHouseEntityRepository = auctionHouseRepository,
            )

        val result = service.getById(1604)

        requireNotNull(result)
        assertSame(authoritativeAuctionHouse, result.auctionHouse)
        verify(exactly = 1) { connectedRealmRepository.save(match { it.auctionHouse === authoritativeAuctionHouse }) }
    }

    @Test
    fun `getById leaves connected realm untouched when link already points at authoritative auction house`() {
        val authoritativeAuctionHouse = AuctionHouse(id = 20, connectedId = 1091, region = Region.Europe)
        val connectedRealm =
            ConnectedRealm(
                id = 1091,
                auctionHouse = authoritativeAuctionHouse,
                realms = mutableListOf(),
            )

        val connectedRealmRepository = mockk<ConnectedRealmRepository>()
        val auctionHouseRepository = mockk<AuctionHouseEntityRepository>()
        every { connectedRealmRepository.findById(1091) } returns Optional.of(connectedRealm)
        every { auctionHouseRepository.findByConnectedId(1091) } returns Optional.of(authoritativeAuctionHouse)

        val service =
            ConnectedRealmService(
                properties = properties,
                blizzardConnectedRealmApiClient = mockk<BlizzardConnectedRealmApiClient>(),
                regionService = mockk<RegionService>(),
                connectedRealmRepository = connectedRealmRepository,
                regionRepository = mockk<RegionRepository>(),
                auctionHouseService = mockk<AuctionHouseService>(),
                connectedRealmBulkSyncService = mockk<ConnectedRealmBulkSyncService>(),
                auctionHouseEntityRepository = auctionHouseRepository,
            )

        val result = service.getById(1091)

        assertSame(connectedRealm, result)
        assertEquals(1091, result?.auctionHouse?.connectedId)
        verify(exactly = 0) { connectedRealmRepository.save(any()) }
    }
}
