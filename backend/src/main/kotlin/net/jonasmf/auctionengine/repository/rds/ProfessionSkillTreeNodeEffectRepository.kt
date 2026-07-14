package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.domain.profession.ParsedSkillTreeNodeEffect
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class ProfessionSkillTreeNodeEffect(
    val nodeId: Long,
    val externalNodeId: Int,
    val skillBonus: Int,
    val craftingCategory: String?,
    val unlockRank: Int,
    val requiredParentRanks: Int,
)

@Repository
class ProfessionSkillTreeNodeEffectRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun replaceNodeEffects(
        nodeId: Long,
        unlockRank: Int,
        effects: List<ParsedSkillTreeNodeEffect>,
    ) {
        jdbcTemplate.update("DELETE FROM profession_skill_tree_node_effect WHERE node_id = ?", nodeId)
        effects.forEach { effect ->
            jdbcTemplate.update(
                """
                INSERT INTO profession_skill_tree_node_effect
                    (node_id, effect_type, skill_bonus, crafting_category, unlock_rank, source)
                VALUES (?, 'SKILL_BONUS', ?, ?, ?, 'description')
                """.trimIndent(),
                nodeId,
                effect.skillBonus,
                effect.craftingCategory,
                unlockRank,
            )
        }
    }

    fun findByTreeId(treeId: Long): List<ProfessionSkillTreeNodeEffect> = findByTreeIds(setOf(treeId))[treeId].orEmpty()

    fun findByTreeIds(treeIds: Collection<Long>): Map<Long, List<ProfessionSkillTreeNodeEffect>> {
        if (treeIds.isEmpty()) return emptyMap()
        val placeholders = treeIds.joinToString(",")
        return jdbcTemplate
            .query(
                """
                SELECT
                    node.tree_id,
                    effect.node_id,
                    node.external_node_id,
                    effect.skill_bonus,
                    effect.crafting_category,
                    effect.unlock_rank,
                    COALESCE(MAX(parent.required_parent_ranks), 0) AS required_parent_ranks
                FROM profession_skill_tree_node node
                    INNER JOIN profession_skill_tree_node_effect effect ON effect.node_id = node.id
                    LEFT JOIN profession_skill_tree_node_parent parent ON parent.node_id = effect.node_id
                WHERE node.tree_id IN ($placeholders)
                GROUP BY node.tree_id, effect.node_id, node.external_node_id, effect.skill_bonus,
                    effect.crafting_category, effect.unlock_rank
                ORDER BY node.tree_id, effect.node_id
                """.trimIndent(),
                { rs, _ ->
                    rs.getLong("tree_id") to
                        ProfessionSkillTreeNodeEffect(
                            nodeId = rs.getLong("node_id"),
                            externalNodeId = rs.getInt("external_node_id"),
                            skillBonus = rs.getInt("skill_bonus"),
                            craftingCategory = rs.getString("crafting_category"),
                            unlockRank = rs.getInt("unlock_rank"),
                            requiredParentRanks = rs.getInt("required_parent_ranks"),
                        )
                },
                *treeIds.toTypedArray(),
            ).groupBy({ it.first }, { it.second })
    }
}
