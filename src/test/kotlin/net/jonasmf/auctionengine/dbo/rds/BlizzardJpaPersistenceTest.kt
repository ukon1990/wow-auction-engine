package net.jonasmf.auctionengine.dbo.rds

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.EntityManager
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.dbo.rds.item.ItemAppearanceDBO
import net.jonasmf.auctionengine.dbo.rds.item.ItemDBO
import net.jonasmf.auctionengine.dbo.rds.profession.ProfessionDBO
import net.jonasmf.auctionengine.dbo.rds.profession.RecipeDBO
import net.jonasmf.auctionengine.dto.item.ItemDTO
import net.jonasmf.auctionengine.dto.itemappearance.ItemAppearanceDTO
import net.jonasmf.auctionengine.dto.profession.SkillTierDTO
import net.jonasmf.auctionengine.dto.recipe.RecipeDTO
import net.jonasmf.auctionengine.mapper.toDBO
import net.jonasmf.auctionengine.mapper.toDomain
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

@Transactional
class BlizzardJpaPersistenceTest : IntegrationTestBase() {
    @Autowired
    lateinit var entityManager: EntityManager

    private val mapper = jacksonObjectMapper()

    @Test
    fun `profession graph persists through jpa`() {
        val skillTierDto: SkillTierDTO = mapper.readValue(loadFixture(this, "/blizzard/profession/356/skill-tier/2911-response.json"))
        val profession =
            net.jonasmf.auctionengine.domain.profession.Profession(
                id = 356,
                name = net.jonasmf.auctionengine.dto.LocaleDTO(en_US = "Fishing", en_GB = "Fishing"),
                description = net.jonasmf.auctionengine.dto.LocaleDTO(en_US = "desc", en_GB = "desc"),
                mediaUrl = "https://example.test/profession/356",
                skillTiers = listOf(skillTierDto.toDomain()),
            )

        entityManager.persist(profession.toDBO())
        entityManager.flush()
        entityManager.clear()

        val loaded = entityManager.find(ProfessionDBO::class.java, 356)

        assertEquals(1, loaded.skillTiers.size)
        assertEquals(6, loaded.skillTiers.first().categories.size)
        assertEquals(51965, loaded.skillTiers.first().categories[1].recipes.first().id)
    }

    @Test
    fun `recipe graph persists through jpa without item rows`() {
        val recipeDto: RecipeDTO = mapper.readValue(loadFixture(this, "/blizzard/recipe/42363-response.json"))

        entityManager.persist(recipeDto.toDomain().toDBO())
        entityManager.flush()
        entityManager.clear()

        val loaded = entityManager.find(RecipeDBO::class.java, 42363)

        assertEquals(171374, loaded.craftedItemId)
        assertEquals(1, loaded.craftedQuantity)
        assertEquals(2, loaded.reagents.size)
        assertEquals(171828, loaded.reagents.first().itemId)
        assertEquals(12, loaded.reagents.first().quantity)
    }

    @Test
    fun `item graph persists through jpa`() {
        val itemDto: ItemDTO = mapper.readValue(loadFixture(this, "/blizzard/item/171374-response.json"))

        entityManager.persist(itemDto.toDomain().toDBO())
        entityManager.flush()
        entityManager.clear()

        val loaded = entityManager.find(ItemDBO::class.java, 171374)

        assertEquals(4, loaded.itemClass.id)
        assertEquals(4, loaded.itemSubclass.subclassId)
        assertEquals(1, loaded.appearances.size)
        assertEquals(42040, loaded.appearances.first().id)
    }

    @Test
    fun `item appearance graph persists through jpa`() {
        val appearanceDto: ItemAppearanceDTO = mapper.readValue(loadFixture(this, "/blizzard/item-appearance/42763-response.json"))

        entityManager.persist(appearanceDto.toDomain().toDBO())
        entityManager.flush()
        entityManager.clear()

        val loaded = entityManager.find(ItemAppearanceDBO::class.java, 42763)

        assertEquals(184300, loaded.itemDisplayInfoId)
        assertEquals(2, loaded.items.size)
        assertEquals(186312, loaded.items.first().id)
        assertEquals(4, loaded.itemClass.id)
        assertEquals(4, loaded.itemSubclass.subclassId)
    }
}
