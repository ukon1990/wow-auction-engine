package net.jonasmf.auctionengine.dto.blizzard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.jonasmf.auctionengine.dto.item.ItemDTO
import net.jonasmf.auctionengine.dto.itemappearance.ItemAppearanceDTO
import net.jonasmf.auctionengine.dto.itemclass.ItemClassDTO
import net.jonasmf.auctionengine.dto.itemclass.ItemSubclassDTO
import net.jonasmf.auctionengine.dto.modifiedcrafting.ModifiedCraftingCategoryDTO
import net.jonasmf.auctionengine.dto.modifiedcrafting.ReagentSlotTypeDTO
import net.jonasmf.auctionengine.dto.recipe.RecipeDTO
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BlizzardResourceDTODeserializationTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `should deserialize recipe fixture`() {
        val dto: RecipeDTO = mapper.readValue(loadFixture(this, "/blizzard/recipe/42363-response.json"))

        assertEquals(42363, dto.id)
        assertEquals(171374, dto.craftedItem?.id)
        assertEquals(1, dto.craftedQuantity?.value)
        assertEquals(2, dto.reagents.size)
        assertEquals(171828, dto.reagents.first().reagent.id)
        assertEquals(1, dto.modifiedCraftingSlots.size)
        assertEquals(46, dto.modifiedCraftingSlots.first().slotType.id)
    }

    @Test
    fun `should deserialize modified crafting category fixture`() {
        val dto: ModifiedCraftingCategoryDTO =
            mapper.readValue(loadFixture(this, "/blizzard/modified-crafting/category/902-response.json"))

        assertEquals(902, dto.id)
        assertEquals("12.0 Optional Reagent - Outdoor Upgrade Dungeon - All", dto.name.en_US)
    }

    @Test
    fun `should deserialize modified crafting reagent slot type fixture`() {
        val dto: ReagentSlotTypeDTO =
            mapper.readValue(loadFixture(this, "/blizzard/modified-crafting/reagent-slot-type/471-response.json"))

        assertEquals(471, dto.id)
        assertEquals(3, dto.compatibleCategories.size)
        assertEquals(878, dto.compatibleCategories.first().id)
    }

    @Test
    fun `should deserialize item fixture`() {
        val dto: ItemDTO = mapper.readValue(loadFixture(this, "/blizzard/item/171374-response.json"))

        assertEquals(171374, dto.id)
        assertEquals(4, dto.itemClass.id)
        assertEquals(4, dto.itemSubclass.id)
        assertEquals(1, dto.appearances.size)
        assertEquals(42040, dto.appearances.first().id)
    }

    @Test
    fun `should deserialize item class fixture`() {
        val dto: ItemClassDTO = mapper.readValue(loadFixture(this, "/blizzard/item-class/7-response.json"))

        assertEquals(7, dto.classId)
        assertEquals(13, dto.itemSubclasses.size)
        assertEquals(19, dto.itemSubclasses.last().id)
    }

    @Test
    fun `should deserialize item subclass fixture`() {
        val dto: ItemSubclassDTO =
            mapper.readValue(loadFixture(this, "/blizzard/item-class/7/item-subclass/19-response.json"))

        assertEquals(7, dto.classId)
        assertEquals(19, dto.subclassId)
        assertEquals(true, dto.hideSubclassInTooltips)
    }

    @Test
    fun `should deserialize item appearance fixture`() {
        val dto: ItemAppearanceDTO =
            mapper.readValue(loadFixture(this, "/blizzard/item-appearance/42763-response.json"))

        assertEquals(42763, dto.id)
        assertEquals(184300, dto.itemDisplayInfoId)
        assertEquals(2, dto.items.size)
        assertEquals(186312, dto.items.first().id)
    }
}
