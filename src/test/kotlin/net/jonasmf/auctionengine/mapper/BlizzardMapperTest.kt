package net.jonasmf.auctionengine.mapper

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.jonasmf.auctionengine.dto.recipe.CraftedQuantityDTO
import net.jonasmf.auctionengine.dto.item.ItemDTO
import net.jonasmf.auctionengine.dto.itemappearance.ItemAppearanceDTO
import net.jonasmf.auctionengine.dto.itemclass.ItemClassDTO
import net.jonasmf.auctionengine.dto.itemclass.ItemSubclassDTO
import net.jonasmf.auctionengine.dto.modifiedcrafting.ModifiedCraftingCategoryDTO
import net.jonasmf.auctionengine.dto.modifiedcrafting.ReagentSlotTypeDTO
import net.jonasmf.auctionengine.dto.profession.SkillTierDTO
import net.jonasmf.auctionengine.dto.recipe.RecipeDTO
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BlizzardMapperTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `should map skill tier dto to domain with recipe stubs`() {
        val dto: SkillTierDTO =
            mapper.readValue(
                loadFixture(this, "/blizzard/profession/356/skill-tier/2911-response.json"),
            )

        val domain = dto.toDomain()

        assertEquals(2911, domain.id)
        assertEquals(6, domain.categories.size)
        assertEquals(
            51945,
            domain.categories
                .first()
                .recipes
                .first()
                .id,
        )
        assertEquals(
            null,
            domain.categories
                .first()
                .recipes
                .first()
                .description,
        )
    }

    @Test
    fun `should map recipe dto to domain with reagents and modified crafting slots`() {
        val dto: RecipeDTO = mapper.readValue(loadFixture(this, "/blizzard/recipe/42363-response.json"))

        val domain = dto.toDomain()

        assertEquals(42363, domain.id)
        assertEquals(171374, domain.craftedItemId)
        assertEquals(1, domain.craftedQuantity)
        assertEquals(2, domain.reagents.size)
        assertEquals(171828, domain.reagents.first().itemId)
        assertEquals(12, domain.reagents.first().quantity)
        assertEquals(1, domain.modifiedCraftingSlots.size)
        assertEquals(46, domain.modifiedCraftingSlots.first().id)
        assertEquals(0, domain.modifiedCraftingSlots.first().displayOrder)
    }

    @Test
    fun `should map modified crafting category dto to domain`() {
        val dto: ModifiedCraftingCategoryDTO =
            mapper.readValue(loadFixture(this, "/blizzard/modified-crafting/category/828-response.json"))

        val domain = dto.toDomain()

        assertEquals(828, domain.id)
        assertEquals("Global Finishing Reagent 03", domain.name.en_US)
    }

    @Test
    fun `should map reagent slot type dto to domain with compatible categories`() {
        val dto: ReagentSlotTypeDTO =
            mapper.readValue(loadFixture(this, "/blizzard/modified-crafting/reagent-slot-type/404-response.json"))

        val domain = dto.toDomain()

        assertEquals(404, domain.id)
        assertEquals(1, domain.compatibleCategories.size)
        assertEquals(776, domain.compatibleCategories.first().id)
    }

    @Test
    fun `should map recipe dto when crafted item reference omits name`() {
        val dto =
            RecipeDTO(
                links = net.jonasmf.auctionengine.dto.Links(net.jonasmf.auctionengine.dto.Link("https://example.test/recipe/1")),
                id = 1,
                name = net.jonasmf.auctionengine.dto.LocaleDTO(en_US = "Recipe", en_GB = "Recipe"),
                media = net.jonasmf.auctionengine.dto.MediaDTO(net.jonasmf.auctionengine.dto.Href("https://example.test/media/1"), 1),
                craftedItem = net.jonasmf.auctionengine.dto.ReferenceDTO(id = 42, key = net.jonasmf.auctionengine.dto.Href("https://example.test/item/42")),
                craftedQuantity = CraftedQuantityDTO(1),
            )

        val domain = dto.toDomain()

        assertEquals(42, domain.craftedItemId)
    }

    @Test
    fun `should round trip profession graph through dbo`() {
        val skillTierDto: SkillTierDTO =
            mapper.readValue(
                loadFixture(this, "/blizzard/profession/356/skill-tier/2911-response.json"),
            )
        val profession =
            net.jonasmf.auctionengine.domain.profession.Profession(
                id = 356,
                name =
                    net.jonasmf.auctionengine.dto
                        .LocaleDTO(en_US = "Fishing", en_GB = "Fishing"),
                description =
                    net.jonasmf.auctionengine.dto
                        .LocaleDTO(en_US = "desc", en_GB = "desc"),
                mediaUrl = "https://example.test/profession/356",
                skillTiers = listOf(skillTierDto.toDomain()),
            )

        val professionDbo = profession.toDBO()
        val roundTrip = professionDbo.toDomain()

        assertEquals("profession", professionDbo.name.sourceType)
        assertEquals("356", professionDbo.name.sourceKey)
        assertEquals("name", professionDbo.name.sourceField)
        assertEquals("skill_tier", professionDbo.skillTiers.first().name.sourceType)
        assertEquals(356, roundTrip.id)
        assertEquals(2911, roundTrip.skillTiers.first().id)
        assertEquals(
            51965,
            roundTrip.skillTiers
                .first()
                .categories[1]
                .recipes
                .first()
                .id,
        )
    }

    @Test
    fun `should round trip item family through dbo`() {
        val itemDto: ItemDTO = mapper.readValue(loadFixture(this, "/blizzard/item/171374-response.json"))
        val itemClassDto: ItemClassDTO = mapper.readValue(loadFixture(this, "/blizzard/item-class/4-response.json"))
        val itemSubclassDto: ItemSubclassDTO =
            mapper.readValue(loadFixture(this, "/blizzard/item-class/4/item-subclass/4-response.json"))
        val itemAppearanceDto: ItemAppearanceDTO =
            mapper.readValue(loadFixture(this, "/blizzard/item-appearance/42763-response.json"))

        val item = itemDto.toDomain()
        val itemClass = itemClassDto.toDomain()
        val itemSubclass = itemSubclassDto.toDomain()
        val itemAppearance = itemAppearanceDto.toDomain()

        val itemDbo = item.toDBO()
        val itemAppearanceDbo = itemAppearance.toDBO()
        val roundTripItem = itemDbo.toDomain()
        val roundTripAppearance = itemAppearanceDbo.toDomain()

        assertEquals("item", itemDbo.name.sourceType)
        assertEquals(item.id.toString(), itemDbo.name.sourceKey)
        assertEquals("item_class", itemDbo.itemClass.name.sourceType)
        assertEquals("item_subclass", itemDbo.itemSubclass.displayName.sourceType)
        assertEquals("inventory_type", itemAppearanceDbo.slot.name.sourceType)
        assertEquals(4, itemClass.id)
        assertEquals(4, itemSubclass.subclassId)
        assertEquals(4, roundTripItem.itemClass.id)
        assertEquals(4, roundTripItem.itemSubclass.subclassId)
        assertEquals(1, roundTripItem.appearances.size)
        assertEquals(2, roundTripAppearance.items.size)
        assertEquals(4, roundTripAppearance.itemClass.id)
        assertEquals(4, roundTripAppearance.itemSubclass.subclassId)
    }
}
