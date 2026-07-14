package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.ProfessionProfileTestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class ProfileCraftabilityQueryPlanIntegrationTest : IntegrationTestBase() {
    @Autowired
    lateinit var profileRepository: ProfileRepository

    @Autowired
    lateinit var effectRepository: ProfessionSkillTreeNodeEffectRepository

    @Autowired
    lateinit var recipeCraftingRuleRepository: RecipeCraftingRuleRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `crafting candidate query uses owner and profession indexes`() {
        val tree = ProfessionProfileTestFixtures.seedMinimalProfessionTree(jdbcTemplate)
        repeat(20) { index ->
            val subject = "perf-user-$index"
            val characterId =
                ProfessionProfileTestFixtures.insertOwnedCharacter(
                    jdbcTemplate,
                    ownerSubject = subject,
                    characterName = "Crafter$index",
                )
            jdbcTemplate.update(
                "INSERT INTO user_character_profession_profile (character_id, profession_id, tree_id, skill_level) VALUES (?, ?, ?, 85)",
                characterId,
                tree.professionId,
                tree.treeId,
            )
        }

        val plan =
            explainPlan(
                """
                SELECT c.id, p.profession_id, p.id AS profile_id
                FROM user_character c
                    INNER JOIN user_character_profession_profile p
                        ON p.character_id = c.id AND p.profession_id IN (${tree.professionId})
                    INNER JOIN profession_skill_tree t ON t.id = p.tree_id
                WHERE c.owner_subject = 'perf-user-0'
                """.trimIndent(),
            )

        assertThat(plan).doesNotContain("type: ALL")
        assertThat(plan).containsAnyOf("idx_user_character_owner_subject", "idx_user_character_owner_updated", "PRIMARY")
        assertThat(profileRepository.findCraftingCandidates("perf-user-0", setOf(tree.professionId))).isNotEmpty
    }

    @Test
    fun `tree effect lookup starts from indexed tree nodes`() {
        val tree = ProfessionProfileTestFixtures.seedMinimalProfessionTree(jdbcTemplate)
        val nodeId = jdbcTemplate.queryForObject("SELECT id FROM profession_skill_tree_node WHERE tree_id = ?", Long::class.java, tree.treeId)!!
        jdbcTemplate.update(
            "INSERT INTO profession_skill_tree_node_effect (node_id, effect_type, skill_bonus, unlock_rank, source) VALUES (?, 'SKILL_BONUS', 5, 0, 'description')",
            nodeId,
        )

        val plan =
            explainPlan(
                """
                SELECT effect.node_id
                FROM profession_skill_tree_node node
                    INNER JOIN profession_skill_tree_node_effect effect ON effect.node_id = node.id
                WHERE node.tree_id = ${tree.treeId}
                """.trimIndent(),
            )

        assertThat(plan).contains("uk_profession_skill_tree_node_tree_external")
        assertThat(effectRepository.findByTreeId(tree.treeId)).hasSize(1)
    }

    @Test
    fun `recipe rule lookup uses primary key`() {
        jdbcTemplate.update(
            "INSERT INTO recipe_crafting_rule (recipe_id, base_skill, quality_thresholds) VALUES (999001, 50, '[80,100]')",
        )

        val plan = explainPlan("SELECT recipe_id FROM recipe_crafting_rule WHERE recipe_id = 999001")
        assertThat(plan).contains("type=const")
        assertThat(recipeCraftingRuleRepository.findByRecipeId(999001)?.baseSkill).isNotNull
    }

    private fun explainPlan(sql: String): String =
        jdbcTemplate
            .queryForList("EXPLAIN $sql")
            .joinToString("\n") { row -> row.entries.joinToString { "${it.key}=${it.value}" } }
}
