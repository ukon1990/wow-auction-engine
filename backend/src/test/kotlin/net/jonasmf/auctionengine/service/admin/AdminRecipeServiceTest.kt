package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.domain.profession.RecipeReagent
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.generated.model.AdminRecipeBulkOverride
import net.jonasmf.auctionengine.generated.model.AdminRecipeBulkOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipeFields
import net.jonasmf.auctionengine.generated.model.AdminRecipeOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipeReagent
import net.jonasmf.auctionengine.generated.model.AdminRecipeReagentRank
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.integration.blizzard.RecipeApiLookup
import net.jonasmf.auctionengine.repository.rds.AdminRecipeRepositoryPort
import net.jonasmf.auctionengine.repository.rds.AdminRecipeRows
import net.jonasmf.auctionengine.repository.rds.AdminRecipeSearchRows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class AdminRecipeServiceTest {
    private val repository = FakeAdminRecipeRepository()
    private val recipeApiClient = FakeRecipeApiClient()
    private val service = AdminRecipeService(repository, recipeApiClient)

    @Test
    fun `upsert override rejects missing base recipe`() {
        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.upsertOverride(42, AdminRecipeOverrideRequest(craftedQuantity = 1))
            }

        assertEquals(404, error.statusCode.value())
        assertEquals("Base recipe not found: 42", error.reason)
    }

    @Test
    fun `upsert override validates duplicate reagent sortOrder`() {
        repository.baseRecipeIds += 338995

        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.upsertOverride(
                    338995,
                    AdminRecipeOverrideRequest(
                        reagents =
                            listOf(
                                AdminRecipeReagent(itemId = 171828, quantity = 1, sortOrder = 0),
                                AdminRecipeReagent(itemId = 171829, quantity = 1, sortOrder = 0),
                            ),
                    ),
                )
            }

        assertEquals(400, error.statusCode.value())
        assertEquals("Duplicate reagent sortOrder: 0", error.reason)
    }

    @Test
    fun `upsert override validates reagent rank must be 1-3`() {
        repository.baseRecipeIds += 338995

        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.upsertOverride(
                    338995,
                    AdminRecipeOverrideRequest(
                        reagents =
                            listOf(
                                AdminRecipeReagent(
                                    itemId = 171828,
                                    quantity = 1,
                                    sortOrder = 0,
                                    ranks = listOf(AdminRecipeReagentRank(rank = 4, itemId = 171828)),
                                ),
                            ),
                    ),
                )
            }

        assertEquals(400, error.statusCode.value())
        assertEquals("reagent.rank must be between 1 and 3", error.reason)
    }

    @Test
    fun `delete override returns 404 when no override`() {
        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.deleteOverride(338995)
            }

        assertEquals(404, error.statusCode.value())
        assertEquals("Recipe override not found: 338995", error.reason)
    }

    @Test
    fun `compare returns field level base override api and effective values`() {
        repository.recipeRows[338995] =
            AdminRecipeRows(
                base =
                    AdminRecipeFields(
                        id = 338995,
                        craftedItemId = 171374,
                        craftedQuantity = 1,
                        rank = 1,
                        mediaUrl = "https://example.test/base.png",
                        reagents = listOf(AdminRecipeReagent(itemId = 171828, quantity = 10, sortOrder = 0)),
                    ),
                override =
                    AdminRecipeFields(
                        id = 338995,
                        rank = 2,
                        overrideNote = "Manual fix",
                    ),
                effective =
                    AdminRecipeFields(
                        id = 338995,
                        craftedItemId = 171374,
                        craftedQuantity = 1,
                        rank = 2,
                        mediaUrl = "https://example.test/base.png",
                        overrideNote = "Manual fix",
                        reagents = listOf(AdminRecipeReagent(itemId = 171828, quantity = 10, sortOrder = 0)),
                    ),
            )
        recipeApiClient.recipes[338995] = recipe(338995)

        val result = service.compareWithApi(338995)

        assertEquals("1", result.fields.getValue("rank").base)
        assertEquals("2", result.fields.getValue("rank").`override`)
        assertEquals("3", result.fields.getValue("rank").api)
        assertEquals("2", result.fields.getValue("rank").effective)
        assertEquals("https://example.test/base.png", result.fields.getValue("mediaUrl").effective)
        assertEquals("171828 x12", result.fields.getValue("reagents").api)
        assertEquals("171828 x10", result.fields.getValue("reagents").effective)
        assertEquals("Manual fix", result.fields.getValue("overrideNote").effective)
    }

    @Test
    fun `bulk upsert overrides rejects duplicate ids in request`() {
        repository.baseRecipeIds += 338995

        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.bulkUpsertOverrides(
                    AdminRecipeBulkOverrideRequest(
                        overrides =
                            listOf(
                                AdminRecipeBulkOverride(338995, AdminRecipeOverrideRequest(craftedQuantity = 1)),
                                AdminRecipeBulkOverride(338995, AdminRecipeOverrideRequest(craftedQuantity = 2)),
                            ),
                    ),
                )
            }

        assertEquals(400, error.statusCode.value())
        assertEquals("Duplicate recipe override id: 338995", error.reason)
    }

    private fun recipe(id: Int) =
        Recipe(
            id = id,
            name = LocaleDTO(en_US = "API recipe", en_GB = "API recipe"),
            mediaUrl = "https://example.test/api.png",
            mediaSourceUrl = "https://example.test/api-source.png",
            rank = 3,
            craftedItemId = 171374,
            craftedQuantity = 1,
            reagents =
                listOf(
                    RecipeReagent(
                        itemId = 171828,
                        name = LocaleDTO(en_US = "Reagent", en_GB = "Reagent"),
                        quantity = 12,
                    ),
                ),
        )
}

private class FakeRecipeApiClient : RecipeApiLookup {
    val recipes = mutableMapOf<Int, Recipe>()

    override fun getById(id: Int): Recipe = recipes.getValue(id)
}

private class FakeAdminRecipeRepository : AdminRecipeRepositoryPort {
    val baseRecipeIds = mutableSetOf<Int>()
    val recipeRows = mutableMapOf<Int, AdminRecipeRows>()
    val overrideIds = mutableSetOf<Int>()
    var lastUpsert: Pair<Int, AdminRecipeOverrideRequest>? = null

    override fun searchRecipes(
        query: String?,
        hasOverride: Boolean?,
        professionId: Int?,
        craftedItemId: Int?,
        page: Int,
        pageSize: Int,
        localeColumnSuffix: String,
    ): AdminRecipeSearchRows = AdminRecipeSearchRows(recipes = emptyList(), totalItems = 0)

    override fun pageMetadata(
        page: Int,
        pageSize: Int,
        totalItems: Long,
    ): PageMetadata = PageMetadata(page = page, pageSize = pageSize, totalItems = totalItems, totalPages = 0)

    override fun findRecipeRows(
        id: Int,
        localeColumnSuffix: String,
    ): AdminRecipeRows? = recipeRows[id]

    override fun hasBaseRecipe(id: Int): Boolean = id in baseRecipeIds

    override fun upsertOverride(
        id: Int,
        request: AdminRecipeOverrideRequest,
    ) {
        lastUpsert = id to request
        overrideIds += id
    }

    override fun deleteOverride(id: Int): Boolean = overrideIds.remove(id)
}
