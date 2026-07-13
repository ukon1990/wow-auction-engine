package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionData
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperCharacter
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfession
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperRecipe
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperSource
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperSourceFilesInner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class NormalizedProfessionImportRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var repository: NormalizedProfessionImportRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `save binds generated enum values as database scalars`() {
        repository.save(payload(), professionCount = 0, recipeCount = 0)

        val stored =
            jdbcTemplate.queryForMap(
                "SELECT contract_version, addon, character_count FROM normalized_profession_import",
            )

        assertEquals(1, (stored["contract_version"] as Number).toInt())
        assertEquals("AuctionHelper", stored["addon"])
        assertEquals(0, (stored["character_count"] as Number).toInt())
    }

    @Test
    fun `save persists character profession and exported recipe knowledge`() {
        jdbcTemplate.update("INSERT INTO profession (id) VALUES (164)")
        val recipe =
            NormalizedAuctionHelperRecipe(
                recipeId = 450216,
                name = "Charged Claymore",
                learned = true,
                qualityOutputItemIds = emptyList(),
                qualityThresholds = emptyList(),
                reagentSlots = emptyList(),
                maxQualityRequiredReagents = emptyList(),
            )
        val profession = NormalizedAuctionHelperProfession(164, "Blacksmithing", listOf(recipe), skillLevel = 85)
        val character = NormalizedAuctionHelperCharacter("eu-realm-character", "Character", "Realm", "EU", listOf(profession))

        repository.save(payload().copy(characters = listOf(character)), 1, 1, "admin-subject")

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_character", Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT skill_level FROM user_character_profession_profile", Int::class.java)).isEqualTo(85)
        assertThat(jdbcTemplate.queryForMap("SELECT recipe_id, recipe_name, learned FROM user_character_profession_recipe"))
            .containsEntry("recipe_id", 450216)
            .containsEntry("recipe_name", "Charged Claymore")
            .containsEntry("learned", true)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profession_skill_tree", Int::class.java)).isZero()
    }

    private fun payload() =
        NormalizedAuctionHelperProfessionData(
            contractVersion = NormalizedAuctionHelperProfessionData.ContractVersion._1,
            source =
                NormalizedAuctionHelperSource(
                    addon = NormalizedAuctionHelperSource.Addon.AUCTION_HELPER,
                    addonVersion = "2.0.0",
                    processorVersion = "test",
                    files =
                        listOf(
                            NormalizedAuctionHelperSourceFilesInner(
                                fileName = "AuctionHelper_Professions.lua",
                                sha256 = "0".repeat(64),
                            ),
                        ),
                ),
            characters = emptyList(),
        )
}
