package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.domain.item.InventoryType
import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.domain.item.ItemClass
import net.jonasmf.auctionengine.domain.item.ItemQuality
import net.jonasmf.auctionengine.domain.item.ItemSubclass
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.generated.model.AdminItemCreateRequest
import net.jonasmf.auctionengine.generated.model.AdminItemFields
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemReference
import net.jonasmf.auctionengine.generated.model.AdminRecipeAssociationRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipeSearchResult
import net.jonasmf.auctionengine.generated.model.GameLocale
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.integration.blizzard.ItemApiLookup
import net.jonasmf.auctionengine.repository.rds.AdminItemRepositoryPort
import net.jonasmf.auctionengine.repository.rds.AdminItemRows
import net.jonasmf.auctionengine.repository.rds.AdminItemSearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class AdminItemServiceTest {
    private val repository = FakeAdminItemRepository()
    private val itemApiClient = FakeItemApiLookup()
    private val service = AdminItemService(repository, itemApiClient)

    @Test
    fun `upsert override rejects missing item`() {
        repository.itemRows -= 42

        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.upsertOverride(42, AdminItemOverrideRequest(mediaUrl = "https://example.test/icon.png"))
            }

        assertEquals(404, error.statusCode.value())
        assertEquals("Item not found: 42", error.reason)
    }

    @Test
    fun `upsert override allows override-only item when full effective fields remain present`() {
        repository.itemRows[500000] =
            AdminItemRows(
                base = null,
                override = AdminItemFields(id = 500000),
                effective = AdminItemFields(id = 500000),
            )

        service.upsertOverride(
            500000,
            AdminItemOverrideRequest(
                nameLocales = GameLocale(enUS = "Manual item"),
                qualityType = "COMMON",
                level = 1,
                requiredLevel = 1,
                mediaUrl = "https://example.test/icon.png",
                itemClassId = 7,
                itemSubclassId = 1,
                inventoryType = "NON_EQUIP",
                purchasePrice = 0,
                sellPrice = 0,
                maxCount = 1,
                isEquippable = false,
                isStackable = true,
                purchaseQuantity = 1,
            ),
        )

        assertEquals(500000, repository.lastUpsert?.first)
    }

    @Test
    fun `create override-only requires localized English name`() {
        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.createOverrideOnly(
                    AdminItemCreateRequest(
                        id = 500000,
                        nameLocales = GameLocale(),
                        qualityType = "COMMON",
                        level = 1,
                        requiredLevel = 1,
                        mediaUrl = "https://example.test/icon.png",
                        itemClassId = 7,
                        itemSubclassId = 1,
                        inventoryType = "NON_EQUIP",
                        purchasePrice = 0,
                        sellPrice = 0,
                        maxCount = 1,
                        isEquippable = false,
                        isStackable = true,
                        purchaseQuantity = 1,
                    ),
                )
            }

        assertEquals(400, error.statusCode.value())
        assertEquals("At least one English item name is required", error.reason)
    }

    @Test
    fun `compare returns field level base override api and effective values`() {
        repository.itemRows[171374] =
            AdminItemRows(
                base =
                    AdminItemFields(
                        id = 171374,
                        quality = AdminItemReference(id = 1, type = "COMMON", name = "Common"),
                        mediaUrl = "https://example.test/base.png",
                    ),
                override =
                    AdminItemFields(
                        id = 171374,
                        quality = AdminItemReference(id = 3, type = "RARE", name = "Rare"),
                    ),
                effective =
                    AdminItemFields(
                        id = 171374,
                        quality = AdminItemReference(id = 3, type = "RARE", name = "Rare"),
                        mediaUrl = "https://example.test/base.png",
                    ),
            )
        itemApiClient.items[171374] = item(171374)

        val result = service.compareWithApi(171374)

        assertEquals("COMMON", result.fields.getValue("qualityType").base)
        assertEquals("RARE", result.fields.getValue("qualityType").`override`)
        assertEquals("UNCOMMON", result.fields.getValue("qualityType").api)
        assertEquals("RARE", result.fields.getValue("qualityType").effective)
        assertEquals("https://example.test/base.png", result.fields.getValue("mediaUrl").effective)
    }

    @Test
    fun `recipe association updates crafted item and quantity`() {
        repository.itemRows[171374] =
            AdminItemRows(
                base = AdminItemFields(id = 171374),
                override = null,
                effective = AdminItemFields(id = 171374),
            )
        repository.recipeIds += 338995

        service.upsertRecipeAssociation(
            171374,
            338995,
            AdminRecipeAssociationRequest(craftedItemId = 171374, craftedQuantity = 2),
        )

        assertEquals(Triple(338995, 171374, 2), repository.lastRecipeCraftedItemUpdate)
    }

    @Test
    fun `search items accepts class and subclass filters without reference lookup`() {
        repository.itemSubclassExists = false

        val result = service.searchItems(null, null, null, null, 1, 1, null, null, 1, 25)

        assertEquals(0, result.items.size)
        assertEquals(1, repository.lastSearch?.itemClassId)
        assertEquals(1, repository.lastSearch?.itemSubclassId)
    }

    @Test
    fun `search items requires class when subclass filter is set`() {
        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.searchItems(null, null, null, null, null, 1, null, null, 1, 25)
            }

        assertEquals(400, error.statusCode.value())
        assertEquals("itemClassId is required when itemSubclassId is set", error.reason)
    }

    @Test
    fun `recipe association rejects mismatched crafted item id`() {
        repository.itemRows[171374] =
            AdminItemRows(
                base = AdminItemFields(id = 171374),
                override = null,
                effective = AdminItemFields(id = 171374),
            )
        repository.recipeIds += 338995

        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.upsertRecipeAssociation(
                    171374,
                    338995,
                    AdminRecipeAssociationRequest(craftedItemId = 19019, craftedQuantity = 1),
                )
            }

        assertEquals(400, error.statusCode.value())
        assertEquals("craftedItemId must match path item id", error.reason)
    }

    private fun item(id: Int) =
        Item(
            id = id,
            name = LocaleDTO(en_US = "API item", en_GB = "API item"),
            quality = ItemQuality("UNCOMMON", LocaleDTO(en_US = "Uncommon", en_GB = "Uncommon")),
            level = 10,
            requiredLevel = 1,
            mediaUrl = "https://example.test/api.png",
            itemClass = ItemClass(7, LocaleDTO(en_US = "Trade Goods", en_GB = "Trade Goods")),
            itemSubclass = ItemSubclass(7, 1, LocaleDTO(en_US = "Parts", en_GB = "Parts")),
            inventoryType = InventoryType("NON_EQUIP", LocaleDTO(en_US = "Non-equippable", en_GB = "Non-equippable")),
            purchasePrice = 0,
            sellPrice = 1,
            maxCount = 200,
            isEquippable = false,
            isStackable = true,
            purchaseQuantity = 1,
        )
}

private data class AdminItemSearchCall(
    val query: String?,
    val hasBase: Boolean?,
    val hasOverride: Boolean?,
    val itemClassId: Int?,
    val itemSubclassId: Int?,
    val expansionId: Int?,
    val hasRecipe: Boolean?,
    val page: Int,
    val pageSize: Int,
    val localeColumnSuffix: String,
)

private class FakeItemApiLookup : ItemApiLookup {
    val items = mutableMapOf<Int, Item>()

    override fun getById(id: Int): Item = items.getValue(id)
}

private class FakeAdminItemRepository : AdminItemRepositoryPort {
    val baseItemIds = mutableSetOf<Int>()
    val itemRows = mutableMapOf<Int, AdminItemRows>()
    val recipeIds = mutableSetOf<Int>()
    val recipeSearchResults = mutableListOf<AdminRecipeSearchResult>()
    var lastUpsert: Pair<Int, AdminItemOverrideRequest>? = null
    var lastRecipeSearch: Triple<String?, Int, String>? = null
    var lastRecipeCraftedItemUpdate: Triple<Int, Int?, Int?>? = null
    var lastSearch: AdminItemSearchCall? = null
    var itemSubclassExists = true

    override fun searchItems(
        query: String?,
        hasBase: Boolean?,
        hasOverride: Boolean?,
        itemClassId: Int?,
        itemSubclassId: Int?,
        expansionId: Int?,
        hasRecipe: Boolean?,
        page: Int,
        pageSize: Int,
        localeColumnSuffix: String,
    ): AdminItemSearchResult {
        lastSearch =
            AdminItemSearchCall(
                query = query,
                hasBase = hasBase,
                hasOverride = hasOverride,
                itemClassId = itemClassId,
                itemSubclassId = itemSubclassId,
                expansionId = expansionId,
                hasRecipe = hasRecipe,
                page = page,
                pageSize = pageSize,
                localeColumnSuffix = localeColumnSuffix,
            )
        return AdminItemSearchResult(items = emptyList(), totalItems = 0)
    }

    override fun pageMetadata(
        page: Int,
        pageSize: Int,
        totalItems: Long,
    ): PageMetadata = PageMetadata(page = page, pageSize = pageSize, totalItems = totalItems, totalPages = 0)

    override fun findItemRows(
        id: Int,
        localeColumnSuffix: String,
    ): AdminItemRows? = itemRows[id]

    override fun hasAnyItemRow(id: Int): Boolean = itemRows.containsKey(id)

    override fun hasBaseItem(id: Int): Boolean = id in baseItemIds

    override fun hasOverrideItem(id: Int): Boolean = itemRows[id]?.override != null

    override fun qualityId(type: String): Long? = 1

    override fun inventoryTypeId(type: String): Long? = 1

    override fun bindingId(type: String): Long? = 1

    override fun itemClassExists(id: Int): Boolean = true

    override fun itemSubclassInternalId(
        classId: Int,
        subclassId: Int,
    ): Long? = if (itemSubclassExists) 1 else null

    override fun expansionExists(expansionId: Int): Boolean = true

    override fun upsertOverride(
        id: Int,
        request: AdminItemOverrideRequest,
        itemSubclassInternalId: Long?,
    ) {
        lastUpsert = id to request
    }

    override fun createOverrideOnly(
        request: AdminItemCreateRequest,
        itemSubclassInternalId: Long,
    ) = Unit

    override fun deleteOverride(id: Int): Boolean = itemRows.remove(id) != null

    override fun recipeExists(recipeId: Int): Boolean = recipeId in recipeIds

    override fun searchRecipes(
        query: String?,
        limit: Int,
        localeColumnSuffix: String,
    ): List<AdminRecipeSearchResult> {
        lastRecipeSearch = Triple(query, limit, localeColumnSuffix)
        return recipeSearchResults
    }

    override fun updateRecipeCraftedItem(
        recipeId: Int,
        craftedItemId: Int?,
        craftedQuantity: Int?,
    ): Boolean {
        lastRecipeCraftedItemUpdate = Triple(recipeId, craftedItemId, craftedQuantity)
        return recipeId in recipeIds
    }
}
