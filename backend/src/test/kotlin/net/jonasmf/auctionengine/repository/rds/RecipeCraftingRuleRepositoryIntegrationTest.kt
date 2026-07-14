package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.ProfessionProfileTestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class RecipeCraftingRuleRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    lateinit var importRepository: NormalizedProfessionImportRepository

    @Autowired
    lateinit var recipeCraftingRuleRepository: RecipeCraftingRuleRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `import upserts recipe crafting rules and re-submit updates without duplicating`() {
        ProfessionProfileTestFixtures.seedMidnightCatalog(jdbcTemplate)
        val character =
            ProfessionProfileTestFixtures.characterWithBlacksmithingRecipe(
                qualityThresholds = listOf(100.toBigDecimal(), 200.toBigDecimal(), 300.toBigDecimal()),
            )
        val payload = ProfessionProfileTestFixtures.normalizedImportPayload(characters = listOf(character))

        importRepository.save(payload, professionCount = 1, recipeCount = 1, ownerSubject = "admin-subject")

        val rule = recipeCraftingRuleRepository.findByRecipeId(450216)
        assertThat(rule).isNotNull
        assertThat(rule!!.baseSkill).isEqualByComparingTo(BigDecimal("50"))
        assertThat(rule.qualityThresholds).containsExactly(100.toBigDecimal(), 200.toBigDecimal(), 300.toBigDecimal())
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recipe_crafting_rule", Int::class.java)).isEqualTo(1)

        val updatedCharacter =
            ProfessionProfileTestFixtures.characterWithBlacksmithingRecipe(
                qualityThresholds = listOf(110.toBigDecimal(), 210.toBigDecimal(), 310.toBigDecimal()),
            )
        importRepository.save(
            ProfessionProfileTestFixtures.normalizedImportPayload(characters = listOf(updatedCharacter)),
            professionCount = 1,
            recipeCount = 1,
            ownerSubject = "admin-subject",
        )

        val updatedRule = recipeCraftingRuleRepository.findByRecipeId(450216)
        assertThat(updatedRule!!.qualityThresholds).containsExactly(110.toBigDecimal(), 210.toBigDecimal(), 310.toBigDecimal())
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recipe_crafting_rule", Int::class.java)).isEqualTo(1)
    }
}
