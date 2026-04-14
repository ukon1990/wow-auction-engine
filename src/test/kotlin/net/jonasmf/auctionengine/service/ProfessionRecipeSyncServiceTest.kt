package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingCategory
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingSlot
import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.ProfessionCategory
import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.integration.blizzard.ModifiedCraftingApiClient
import net.jonasmf.auctionengine.integration.blizzard.ProfessionApiClient
import net.jonasmf.auctionengine.integration.blizzard.RecipeApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProfessionRecipeSyncServiceTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe),
        )
    private val professionApiClient = mockk<ProfessionApiClient>()
    private val recipeApiClient = mockk<RecipeApiClient>()
    private val modifiedCraftingApiClient = mockk<ModifiedCraftingApiClient>()
    private val bulkSyncService = mockk<ProfessionRecipeBulkSyncService>()

    private fun createService() =
        ProfessionRecipeSyncService(
            properties = properties,
            professionApiClient = professionApiClient,
            recipeApiClient = recipeApiClient,
            modifiedCraftingApiClient = modifiedCraftingApiClient,
            professionRecipeBulkSyncService = bulkSyncService,
        )

    @Test
    fun `syncRegion downloads skill tier recipes and persists tier with metadata`() {
        val service = createService()
        val profession = professionWithRecipeIds(101, listOf(1001, 1002))
        val recipes = listOf(recipeDetail(1001), recipeDetail(1002))
        val categories = listOf(ModifiedCraftingCategory(10, locale("Category")))
        val slots = listOf(ModifiedCraftingSlot(20, locale("Slot"), categories))
        val skillTierSlot = slot<SkillTier>()
        val recipesSlot = slot<List<Recipe>>()
        val slotsSlot = slot<List<ModifiedCraftingSlot>>()

        every { professionApiClient.getAll(Region.Europe) } returns listOf(profession)
        every { recipeApiClient.getById(1001, Region.Europe) } returns recipes[0]
        every { recipeApiClient.getById(1002, Region.Europe) } returns recipes[1]
        every { modifiedCraftingApiClient.getAllCategories(Region.Europe) } returns categories
        every { modifiedCraftingApiClient.getAllSlotTypes(Region.Europe) } returns slots
        every { bulkSyncService.syncModifiedCraftingMetadata(categories, slots) } returns Unit
        every {
            bulkSyncService.syncProfessionSkillTier(any(), capture(skillTierSlot), capture(recipesSlot), capture(slotsSlot))
        } returns summary(recipes = 2, slots = 1)

        val result = service.syncRegion(Region.Europe)

        assertEquals(1, result.professionsFetched)
        assertEquals(1, result.skillTiersFetched)
        assertEquals(2, result.recipeReferencesDiscovered)
        assertEquals(2, result.recipesFetched)
        assertEquals(0, result.recipeFailures)
        assertEquals(2, result.persistenceSummary.recipesUpserted)
        assertEquals(101 + 1, skillTierSlot.captured.id)
        assertEquals(listOf(1001, 1002), recipesSlot.captured.map { it.id }.sorted())
        assertEquals(slots, slotsSlot.captured)
    }

    @Test
    fun `syncRegion deduplicates repeated recipe ids before fetching details`() {
        val service = createService()
        val profession = professionWithRecipeIds(201, listOf(2001, 2001, 2002))

        every { professionApiClient.getAll(Region.Europe) } returns listOf(profession)
        every { recipeApiClient.getById(2001, Region.Europe) } returns recipeDetail(2001)
        every { recipeApiClient.getById(2002, Region.Europe) } returns recipeDetail(2002)
        every { modifiedCraftingApiClient.getAllCategories(Region.Europe) } returns emptyList()
        every { modifiedCraftingApiClient.getAllSlotTypes(Region.Europe) } returns emptyList()
        every { bulkSyncService.syncModifiedCraftingMetadata(emptyList(), emptyList()) } returns Unit
        every { bulkSyncService.syncProfessionSkillTier(any(), any(), any(), any()) } returns summary(recipes = 2, slots = 0)

        service.syncRegion(Region.Europe)

        verify(exactly = 1) { recipeApiClient.getById(2001, Region.Europe) }
        verify(exactly = 1) { recipeApiClient.getById(2002, Region.Europe) }
    }

    @Test
    fun `syncAllConfiguredRegions only syncs configured static data region`() {
        val multiRegionProperties =
            BlizzardApiProperties(
                baseUrl = "https://example.test/",
                tokenUrl = "https://example.test/token",
                clientId = "id",
                clientSecret = "secret",
                regions = listOf(Region.Korea, Region.Taiwan),
                staticDataRegion = Region.Europe,
            )
        val service =
            ProfessionRecipeSyncService(
                properties = multiRegionProperties,
                professionApiClient = professionApiClient,
                recipeApiClient = recipeApiClient,
                modifiedCraftingApiClient = modifiedCraftingApiClient,
                professionRecipeBulkSyncService = bulkSyncService,
            )

        every { professionApiClient.getAll(Region.Europe) } returns emptyList()
        every { modifiedCraftingApiClient.getAllCategories(Region.Europe) } returns emptyList()
        every { modifiedCraftingApiClient.getAllSlotTypes(Region.Europe) } returns emptyList()
        every { bulkSyncService.syncModifiedCraftingMetadata(emptyList(), emptyList()) } returns Unit

        val results = service.syncAllConfiguredRegions()

        assertEquals(1, results.size)
        assertEquals(Region.Europe, results.single().region)
        verify(exactly = 1) { professionApiClient.getAll(Region.Europe) }
        verify(exactly = 0) { professionApiClient.getAll(Region.Korea) }
        verify(exactly = 0) { professionApiClient.getAll(Region.Taiwan) }
    }

    @Test
    fun `syncRegion skips tier persistence after recipe fetch failure`() {
        val service = createService()
        val profession = professionWithRecipeIds(301, listOf(3001, 3002))

        every { professionApiClient.getAll(Region.Europe) } returns listOf(profession)
        every { recipeApiClient.getById(3001, Region.Europe) } returns recipeDetail(3001)
        every { recipeApiClient.getById(3002, Region.Europe) } throws RuntimeException("boom")
        every { modifiedCraftingApiClient.getAllCategories(Region.Europe) } returns emptyList()
        every { modifiedCraftingApiClient.getAllSlotTypes(Region.Europe) } returns emptyList()
        every { bulkSyncService.syncModifiedCraftingMetadata(emptyList(), emptyList()) } returns Unit

        val result = service.syncRegion(Region.Europe)

        assertEquals(1, result.recipesFetched)
        assertEquals(1, result.recipeFailures)
        assertEquals(0, result.persistenceSummary.recipesUpserted)
        verify(exactly = 0) { bulkSyncService.syncProfessionSkillTier(any(), any(), any(), any()) }
    }

    private fun professionWithRecipeIds(
        professionId: Int,
        recipeIds: List<Int>,
    ): Profession =
        Profession(
            id = professionId,
            name = locale("Profession $professionId"),
            description = locale("Profession $professionId desc"),
            mediaUrl = "https://example.test/profession/$professionId",
            skillTiers =
                listOf(
                    SkillTier(
                        id = professionId + 1,
                        name = locale("Tier"),
                        minimumSkillLevel = 1,
                        maximumSkillLevel = 100,
                        categories =
                            listOf(
                                ProfessionCategory(
                                    name = locale("Category"),
                                    recipes = recipeIds.map(::recipeStub),
                                ),
                            ),
                    ),
                ),
        )

    private fun recipeStub(id: Int): Recipe = Recipe(id = id, name = locale("Recipe $id"))

    private fun recipeDetail(id: Int): Recipe =
        Recipe(
            id = id,
            name = locale("Recipe $id"),
            craftedItemId = id + 10_000,
            craftedQuantity = 1,
        )

    private fun summary(
        recipes: Int,
        slots: Int,
    ) =
        ProfessionRecipePersistenceSummary(
            professionsUpserted = 1,
            skillTiersUpserted = 1,
            categoriesReplaced = 1,
            recipesUpserted = recipes,
            reagentsReplaced = 0,
            recipeSlotsReplaced = slots,
            modifiedCraftingCategoriesUpserted = 0,
            modifiedCraftingSlotsUpserted = 0,
            slotCategoryLinksReplaced = 0,
        )

    private fun locale(value: String) = LocaleDTO(en_US = value, en_GB = value)
}
