package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionData
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperCharacter
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfession
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperRecipe
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentAllocation
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentEntry
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentNode
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalents
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentTab
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentTree
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
    lateinit var profileRepository: ProfileRepository

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

    @Test
    fun `save attaches source guid to an existing character matched by location and name`() {
        jdbcTemplate.update(
            """INSERT INTO user_character (owner_subject, region, realm_name, character_name)
                VALUES ('admin-subject', 'eu', 'Realm', 'Character')""".trimIndent(),
        )
        val existingId = jdbcTemplate.queryForObject("SELECT id FROM user_character", Long::class.java)
        val character = NormalizedAuctionHelperCharacter("eu-realm-character", "Character", "Realm", "EU", emptyList())

        repository.save(payload().copy(characters = listOf(character)), 0, 0, "admin-subject")

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_character", Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForMap("SELECT id, source_guid FROM user_character"))
            .containsEntry("id", existingId)
            .containsEntry("source_guid", "eu-realm-character")
    }

    @Test
    fun `save upserts config tree definitions and active character allocations without replacing other configs`() {
        jdbcTemplate.update("INSERT INTO profession (id) VALUES (164)")
        jdbcTemplate.update("INSERT INTO skill_tier (id, minimum_skill_level, maximum_skill_level, profession_id) VALUES (2907, 1, 100, 164)")
        jdbcTemplate.update(
            "INSERT INTO locale (source_type, source_key, source_field, en_us) VALUES ('expansion', '12', 'name', 'Midnight')",
        )
        jdbcTemplate.update(
            """INSERT INTO expansion (id, slug, name_id, major_version, display_order)
                VALUES (12, 'midnight', (SELECT id FROM locale WHERE source_type = 'expansion' AND source_key = '12'), 12, 120)""".trimIndent(),
        )
        val tree =
            NormalizedAuctionHelperTalentTree(
                treeId = 82167859,
                skillLineId = 2907,
                expansionId = 12,
                name = "Midnight Blacksmithing",
                tabs =
                    listOf(
                        NormalizedAuctionHelperTalentTab(
                            tabId = 1068,
                            name = "Craftsmithing",
                            description = "Craft professional equipment.",
                            nodes =
                                listOf(
                                    NormalizedAuctionHelperTalentNode(
                                        nodeId = 104230,
                                        maxRanks = 1,
                                        requiredRank = 25,
                                        description = "Consume less Concentration.",
                                        propertyEntries =
                                            listOf(
                                                NormalizedAuctionHelperTalentEntry(
                                                    entryId = 128830,
                                                    rankLimit = 1,
                                                    description = "Consume 5% less Concentration.",
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
        val talents = NormalizedAuctionHelperTalents(listOf(tree), listOf(NormalizedAuctionHelperTalentAllocation(104230, 128830, 1)))
        val profession = NormalizedAuctionHelperProfession(164, "Blacksmithing", emptyList(), activeSkillLineId = 2907, talents = talents)
        val character = NormalizedAuctionHelperCharacter("eu-realm-character", "Character", "Realm", "EU", listOf(profession))

        repository.save(payload().copy(characters = listOf(character)), 1, 0, "admin-subject")
        repository.save(payload().copy(characters = listOf(character)), 1, 0, "admin-subject")

        assertThat(jdbcTemplate.queryForMap("SELECT expansion_id, profession_id, skill_line_id, config_id FROM profession_skill_tree"))
            .containsEntry("expansion_id", 12)
            .containsEntry("profession_id", 164)
            .containsEntry("skill_line_id", 2907)
            .containsEntry("config_id", 82167859L)
        assertThat(jdbcTemplate.queryForMap("SELECT external_tab_id, name FROM profession_skill_tree_tab"))
            .containsEntry("external_tab_id", 1068)
            .containsEntry("name", "Craftsmithing")
        assertThat(jdbcTemplate.queryForMap("SELECT external_node_id, max_ranks, required_rank FROM profession_skill_tree_node"))
            .containsEntry("external_node_id", 104230)
            .containsEntry("max_ranks", 1)
            .containsEntry("required_rank", 25)
        assertThat(jdbcTemplate.queryForMap("SELECT external_entry_id, rank_limit FROM profession_skill_tree_entry"))
            .containsEntry("external_entry_id", 128830)
            .containsEntry("rank_limit", 1)
        assertThat(jdbcTemplate.queryForObject("SELECT rank FROM user_character_profession_allocation", Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profession_skill_tree", Int::class.java)).isEqualTo(1)
        assertThat(profileRepository.listTrees(12, 164).single().tabs.single().nodes.single().externalNodeId).isEqualTo(104230)

        val recipeOnlyProfession = profession.copy(activeSkillLineId = null, talents = null, skillLevel = 90)
        repository.save(payload().copy(characters = listOf(character.copy(professions = listOf(recipeOnlyProfession)))), 1, 0, "admin-subject")

        assertThat(jdbcTemplate.queryForObject("SELECT tree_id FROM user_character_profession_profile", Long::class.java)).isNotNull()
        assertThat(jdbcTemplate.queryForObject("SELECT skill_level FROM user_character_profession_profile", Int::class.java)).isEqualTo(90)
        assertThat(jdbcTemplate.queryForObject("SELECT rank FROM user_character_profession_allocation", Int::class.java)).isEqualTo(1)
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
