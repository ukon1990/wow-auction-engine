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
import net.jonasmf.auctionengine.repository.rds.ExpansionRangeItemDiscovery
import net.jonasmf.auctionengine.repository.rds.ItemJdbcRepository
import net.jonasmf.auctionengine.repository.rds.ItemPersistenceSummary
import net.jonasmf.auctionengine.repository.rds.ItemRetryEligibility
import net.jonasmf.auctionengine.repository.rds.ItemSourceDiscovery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
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
    private val blizzardMediaService = mockk<BlizzardMediaService>()
    private val clock = Clock.fixed(Instant.parse("2026-04-14T08:00:00Z"), ZoneOffset.UTC)

    private fun createService() =
        ItemSyncService(
            properties = properties,
            itemApiClient = itemApiClient,
            itemJdbcRepository = itemJdbcRepository,
            itemBulkSyncService = itemBulkSyncService,
            blizzardMediaService = blizzardMediaService,
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
        every { blizzardMediaService.resolveItem(Region.Europe, item) } returns item
        every { itemBulkSyncService.syncItems(listOf(item)) } returns summary(items = 1)
        every { itemJdbcRepository.classifyItemRetryEligibility(listOf(1001, 1003), any()) } returns
            ItemRetryEligibility(
                retryableIds = listOf(1001, 1003),
                cooldownSkippedIds = emptyList(),
                manualDisabledIds = emptyList(),
            )
        every { itemJdbcRepository.findItemFetchFailureStates(listOf(1001, 1003)) } returns emptyMap()
        every { itemJdbcRepository.upsertItemFetchFailureState(1003, 1, null, "boom", any(), any(), false) } returns Unit
        every { itemJdbcRepository.clearItemFetchFailureStates(listOf(1001)) } returns Unit

        val result = service.syncRegion(Region.Europe)

        assertEquals(2, result.auctionSourceCount)
        assertEquals(2, result.recipeCraftedSourceCount)
        assertEquals(2, result.recipeReagentSourceCount)
        assertEquals(4, result.candidateItemCount)
        assertEquals(2, result.existingItemCount)
        assertEquals(2, result.missingItemCount)
        assertEquals(0, result.skippedByBackoffCount)
        assertEquals(0, result.skippedManualDisabledCount)
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
        every { blizzardMediaService.resolveItem(Region.Europe, item) } returns item
        every { itemBulkSyncService.syncItems(listOf(item)) } returns summary(items = 1)
        every { itemJdbcRepository.classifyItemRetryEligibility(listOf(2001), any()) } returns
            ItemRetryEligibility(
                retryableIds = listOf(2001),
                cooldownSkippedIds = emptyList(),
                manualDisabledIds = emptyList(),
            )
        every { itemJdbcRepository.findItemFetchFailureStates(listOf(2001)) } returns emptyMap()
        every { itemJdbcRepository.clearItemFetchFailureStates(listOf(2001)) } returns Unit

        val result = service.syncRegion(Region.Europe)

        assertEquals(1, result.auctionSourceCount)
        assertEquals(0, result.recipeCraftedSourceCount)
        assertEquals(0, result.recipeReagentSourceCount)
        assertEquals(0, result.skippedByBackoffCount)
        assertEquals(0, result.skippedManualDisabledCount)
        assertEquals(1, result.fetchedItemCount)
        assertEquals(1, result.persistedItemCount)
        verify(exactly = 1) { itemJdbcRepository.findMissingItemIdsForDate(any()) }
    }

    @Test
    fun `syncRegion skips non-retryable items`() {
        val service = createService()

        every { itemJdbcRepository.findMissingItemIdsForDate(any()) } returns
            ItemSourceDiscovery(
                auctionSourceCount = 2,
                recipeCraftedSourceCount = 0,
                recipeReagentSourceCount = 0,
                candidateItemCount = 2,
                existingItemCount = 0,
                missingItemIds = listOf(3001, 3002),
            )
        every { itemJdbcRepository.classifyItemRetryEligibility(listOf(3001, 3002), any()) } returns
            ItemRetryEligibility(
                retryableIds = emptyList(),
                cooldownSkippedIds = listOf(3001),
                manualDisabledIds = listOf(3002),
            )
        every { itemJdbcRepository.findItemFetchFailureStates(emptyList()) } returns emptyMap()
        every { itemJdbcRepository.findExistingItemIds(emptyList()) } returns emptySet()

        val result = service.syncRegion(Region.Europe)

        assertEquals(2, result.missingItemCount)
        assertEquals(1, result.skippedByBackoffCount)
        assertEquals(1, result.skippedManualDisabledCount)
        assertEquals(0, result.fetchedItemCount)
        verify(exactly = 0) { itemApiClient.getById(any(), any()) }
    }

    @Test
    fun `syncMissingItemsFromEnabledExpansionRanges fetches missing ids from expansion ranges`() {
        val service = createService()
        val item = item(4001)

        every { itemJdbcRepository.findMissingItemIdsForEnabledExpansionRanges() } returns
            ExpansionRangeItemDiscovery(
                candidateItemCount = 2,
                existingItemCount = 1,
                missingItemIds = listOf(4001),
            )
        every { itemJdbcRepository.classifyItemRetryEligibility(listOf(4001), any()) } returns
            ItemRetryEligibility(
                retryableIds = listOf(4001),
                cooldownSkippedIds = emptyList(),
                manualDisabledIds = emptyList(),
            )
        every { itemJdbcRepository.findItemFetchFailureStates(listOf(4001)) } returns emptyMap()
        every { itemApiClient.getById(4001, Region.Europe) } returns item
        every { blizzardMediaService.resolveItem(Region.Europe, item) } returns item
        every { itemBulkSyncService.syncItems(listOf(item)) } returns summary(items = 1)
        every { itemJdbcRepository.clearItemFetchFailureStates(listOf(4001)) } returns Unit
        every { itemJdbcRepository.findExistingItemIds(listOf(4001)) } returns setOf(4001)

        val result = service.syncMissingItemsFromEnabledExpansionRanges(Region.Europe)

        assertEquals(2, result.candidateItemCount)
        assertEquals(1, result.existingItemCount)
        assertEquals(1, result.missingItemCount)
        assertEquals(1, result.fetchedItemCount)
        verify(exactly = 1) { itemJdbcRepository.findMissingItemIdsForEnabledExpansionRanges() }
        verify(exactly = 1) { itemApiClient.getById(4001, Region.Europe) }
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
