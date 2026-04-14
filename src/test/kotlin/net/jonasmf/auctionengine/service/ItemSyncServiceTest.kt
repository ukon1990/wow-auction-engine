package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.item.InventoryType
import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.domain.item.ItemBinding
import net.jonasmf.auctionengine.domain.item.ItemClass
import net.jonasmf.auctionengine.domain.item.ItemQuality
import net.jonasmf.auctionengine.domain.item.ItemSubclass
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.integration.blizzard.ItemApiClient
import net.jonasmf.auctionengine.repository.rds.ItemJdbcRepository
import net.jonasmf.auctionengine.repository.rds.ItemPersistenceSummary
import net.jonasmf.auctionengine.repository.rds.ItemSourceDiscovery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ItemSyncServiceTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe),
        )
    private val itemApiClient = mockk<ItemApiClient>()
    private val itemJdbcRepository = mockk<ItemJdbcRepository>()
    private val itemBulkSyncService = mockk<ItemBulkSyncService>()
    private val clock = Clock.fixed(Instant.parse("2026-04-14T08:00:00Z"), ZoneOffset.UTC)

    private fun createService() =
        ItemSyncService(
            properties = properties,
            itemApiClient = itemApiClient,
            itemJdbcRepository = itemJdbcRepository,
            itemBulkSyncService = itemBulkSyncService,
            clock = clock,
        )

    @Test
    fun `syncRegion deduplicates sources and skips existing items before fetch`() {
        val service = createService()
        val item = item(1001)

        every { itemJdbcRepository.findMissingItemIdsForDate(any()) } returns
            ItemSourceDiscovery(
                auctionSourceCount = 2,
                recipeCraftedSourceCount = 2,
                recipeReagentSourceCount = 2,
                candidateItemCount = 4,
                existingItemCount = 2,
                missingItemIds = listOf(1001, 1003),
            )
        every { itemJdbcRepository.findExistingItemIds(listOf(1001)) } returns setOf(1001)
        every { itemApiClient.getById(1001, Region.Europe) } returns item
        every { itemApiClient.getById(1003, Region.Europe) } throws RuntimeException("boom")
        every { itemBulkSyncService.syncItems(listOf(item)) } returns summary(items = 1)

        val result = service.syncRegion(Region.Europe)

        assertEquals(2, result.auctionSourceCount)
        assertEquals(2, result.recipeCraftedSourceCount)
        assertEquals(2, result.recipeReagentSourceCount)
        assertEquals(4, result.candidateItemCount)
        assertEquals(2, result.existingItemCount)
        assertEquals(2, result.missingItemCount)
        assertEquals(1, result.fetchedItemCount)
        assertEquals(1, result.itemFetchFailures)
        assertEquals(1, result.persistedItemCount)
        assertEquals(1, result.persistenceSummary.itemsUpserted)
        verify(exactly = 1) { itemApiClient.getById(1001, Region.Europe) }
        verify(exactly = 1) { itemApiClient.getById(1003, Region.Europe) }
    }

    @Test
    fun `syncRegion uses combined source discovery query`() {
        val service = createService()
        val item = item(2001)

        every { itemJdbcRepository.findMissingItemIdsForDate(any()) } returns
            ItemSourceDiscovery(
                auctionSourceCount = 1,
                recipeCraftedSourceCount = 0,
                recipeReagentSourceCount = 0,
                candidateItemCount = 1,
                existingItemCount = 0,
                missingItemIds = listOf(2001),
            )
        every { itemJdbcRepository.findExistingItemIds(listOf(2001)) } returns setOf(2001)
        every { itemApiClient.getById(2001, Region.Europe) } returns item
        every { itemBulkSyncService.syncItems(listOf(item)) } returns summary(items = 1)

        val result = service.syncRegion(Region.Europe)

        assertEquals(1, result.auctionSourceCount)
        assertEquals(0, result.recipeCraftedSourceCount)
        assertEquals(0, result.recipeReagentSourceCount)
        assertEquals(1, result.fetchedItemCount)
        assertEquals(1, result.persistedItemCount)
        verify(exactly = 1) { itemJdbcRepository.findMissingItemIdsForDate(any()) }
    }

    private fun item(id: Int) =
        Item(
            id = id,
            name = locale("Item $id"),
            quality = ItemQuality("COMMON", locale("Common")),
            level = 10,
            requiredLevel = 5,
            mediaUrl = "https://example.test/item/$id",
            itemClass = ItemClass(2, locale("Weapon")),
            itemSubclass = ItemSubclass(2, 0, locale("Axe")),
            inventoryType = InventoryType("WEAPON", locale("Weapon")),
            binding = ItemBinding("ON_EQUIP", locale("Binds when equipped")),
            purchasePrice = 100,
            sellPrice = 10,
            maxCount = 1,
            isEquippable = true,
            isStackable = false,
            purchaseQuantity = 1,
        )

    private fun summary(items: Int) =
        ItemPersistenceSummary(
            localesUpserted = 1,
            itemQualitiesUpserted = 1,
            inventoryTypesUpserted = 1,
            itemBindingsUpserted = 1,
            itemClassesUpserted = 1,
            itemSubclassesUpserted = 1,
            itemAppearanceReferencesUpserted = 0,
            itemsUpserted = items,
            itemAppearanceLinksUpserted = 0,
        )

    private fun locale(value: String) = LocaleDTO(en_US = value, en_GB = value)
}
