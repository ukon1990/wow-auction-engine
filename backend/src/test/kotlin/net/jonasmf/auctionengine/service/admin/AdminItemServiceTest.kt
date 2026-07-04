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

private class FakeItemApiLookup : ItemApiLookup {
    val items = mutableMapOf<Int, Item>()

    override fun getById(id: Int): Item = items.getValue(id)
}

private class FakeAdminItemRepository : AdminItemRepositoryPort {
    val baseItemIds = mutableSetOf<Int>()
    val itemRows = mutableMapOf<Int, AdminItemRows>()
    var lastUpsert: Pair<Int, AdminItemOverrideRequest>? = null

    override fun searchItems(
        query: String?,
        hasBase: Boolean?,
        hasOverride: Boolean?,
        page: Int,
        pageSize: Int,
        localeColumnSuffix: String,
    ): AdminItemSearchResult = AdminItemSearchResult(items = emptyList(), totalItems = 0)

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
    ): Long? = 1

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
}
