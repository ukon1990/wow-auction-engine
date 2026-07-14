package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.ProfessionProfileTestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class NormalizedAuctionHelperProfessionInspectionServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    lateinit var service: NormalizedAuctionHelperProfessionInspectionService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `inspect persists normalized import and character profile through real repository`() {
        ProfessionProfileTestFixtures.seedMidnightCatalog(jdbcTemplate)
        val character = ProfessionProfileTestFixtures.characterWithBlacksmithingRecipe()
        val payload = ProfessionProfileTestFixtures.normalizedImportPayload(characters = listOf(character))

        val result = service.inspect(payload, "admin-subject")

        assertThat(result.imported).isTrue()
        assertThat(result.charactersFound).isEqualTo(1)
        assertThat(result.recipesFound).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM normalized_profession_import", Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_character", Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT skill_level FROM user_character_profession_profile", Int::class.java)).isEqualTo(85)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profession_skill_tree", Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_character_profession_recipe WHERE learned = TRUE", Int::class.java)).isEqualTo(1)
    }
}
