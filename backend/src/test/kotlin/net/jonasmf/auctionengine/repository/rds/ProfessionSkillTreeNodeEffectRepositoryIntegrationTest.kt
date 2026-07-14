package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentEntry
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentNode
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentTab
import net.jonasmf.auctionengine.testsupport.ProfessionProfileTestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class ProfessionSkillTreeNodeEffectRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    lateinit var importRepository: NormalizedProfessionImportRepository

    @Autowired
    lateinit var effectRepository: ProfessionSkillTreeNodeEffectRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `tree import persists structured skill bonus effects from milestone descriptions`() {
        ProfessionProfileTestFixtures.seedMidnightCatalog(jdbcTemplate)
        val tree =
            ProfessionProfileTestFixtures.blacksmithingTreeWithSingleNode().copy(
                tabs =
                    listOf(
                        NormalizedAuctionHelperTalentTab(
                            tabId = 1068,
                            name = "Craftsmithing",
                            description = "Craft professional equipment.",
                            nodes =
                                listOf(
                                    NormalizedAuctionHelperTalentNode(
                                        nodeId = 104229,
                                        maxRanks = 30,
                                        name = "Craftsmithing",
                                        propertyEntries =
                                            listOf(
                                                NormalizedAuctionHelperTalentEntry(
                                                    entryId = 128829,
                                                    rankLimit = 30,
                                                    name = "Waist mastery",
                                                    description = "Gain +5 Skill when crafting waist armor.",
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
        val profession =
            ProfessionProfileTestFixtures.characterWithBlacksmithingRecipe().professions.single().copy(
                talents =
                    net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalents(
                        listOf(tree),
                        emptyList(),
                    ),
            )
        val character =
            ProfessionProfileTestFixtures.characterWithBlacksmithingRecipe().copy(
                professions = listOf(profession),
            )
        importRepository.save(
            ProfessionProfileTestFixtures.normalizedImportPayload(characters = listOf(character)),
            professionCount = 1,
            recipeCount = 1,
            ownerSubject = "admin-subject",
        )

        val treeId = jdbcTemplate.queryForObject("SELECT id FROM profession_skill_tree LIMIT 1", Long::class.java)!!
        val effects = effectRepository.findByTreeId(treeId)

        assertThat(effects).hasSize(1)
        assertThat(effects.single().skillBonus).isEqualTo(5)
        assertThat(effects.single().craftingCategory).isEqualTo("waist armor")
    }
}
