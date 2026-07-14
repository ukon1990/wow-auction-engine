package net.jonasmf.auctionengine.repository.rds

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperMaxQualityReagent
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperRecipe
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal

data class RecipeCraftingRule(
    val recipeId: Int,
    val baseSkill: BigDecimal?,
    val baseDifficulty: BigDecimal?,
    val bonusSkill: BigDecimal?,
    val qualityThresholds: List<BigDecimal>,
    val requiredReagentSkillDelta: BigDecimal?,
    val maxQualityRequiredReagents: List<NormalizedAuctionHelperMaxQualityReagent>,
)

@Repository
class RecipeCraftingRuleRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val objectMapper = jacksonObjectMapper()

    fun upsert(
        recipe: NormalizedAuctionHelperRecipe,
        sourceImportId: Long,
    ) {
        if (!recipe.hasCraftingRuleData()) return
        jdbcTemplate.update(
            """
            INSERT INTO recipe_crafting_rule (
                recipe_id, base_skill, base_difficulty, bonus_skill, quality_thresholds,
                required_reagent_skill_delta, max_quality_required_reagents, source_import_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                base_skill = VALUES(base_skill),
                base_difficulty = VALUES(base_difficulty),
                bonus_skill = VALUES(bonus_skill),
                quality_thresholds = VALUES(quality_thresholds),
                required_reagent_skill_delta = VALUES(required_reagent_skill_delta),
                max_quality_required_reagents = VALUES(max_quality_required_reagents),
                source_import_id = VALUES(source_import_id),
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent(),
            recipe.recipeId,
            recipe.baseSkill,
            recipe.baseDifficulty,
            recipe.bonusSkill,
            recipe.qualityThresholds.takeIf { it.isNotEmpty() }?.let(objectMapper::writeValueAsString),
            recipe.requiredReagentSkillDelta,
            recipe.maxQualityRequiredReagents.takeIf { it.isNotEmpty() }?.let(objectMapper::writeValueAsString),
            sourceImportId,
        )
    }

    fun findByRecipeId(recipeId: Int): RecipeCraftingRule? = findByRecipeIds(setOf(recipeId))[recipeId]

    fun findByRecipeIds(recipeIds: Collection<Int>): Map<Int, RecipeCraftingRule> {
        if (recipeIds.isEmpty()) return emptyMap()
        val placeholders = recipeIds.joinToString(",")
        return jdbcTemplate
            .query(
                """
                SELECT recipe_id, base_skill, base_difficulty, bonus_skill, quality_thresholds,
                    required_reagent_skill_delta, max_quality_required_reagents
                FROM recipe_crafting_rule
                WHERE recipe_id IN ($placeholders)
                """.trimIndent(),
                { rs, _ ->
                    rs.getInt("recipe_id") to
                        RecipeCraftingRule(
                            recipeId = rs.getInt("recipe_id"),
                            baseSkill = rs.getBigDecimal("base_skill"),
                            baseDifficulty = rs.getBigDecimal("base_difficulty"),
                            bonusSkill = rs.getBigDecimal("bonus_skill"),
                            qualityThresholds = rs.getString("quality_thresholds")?.let { objectMapper.readValue(it) } ?: emptyList(),
                            requiredReagentSkillDelta = rs.getBigDecimal("required_reagent_skill_delta"),
                            maxQualityRequiredReagents = rs.getString("max_quality_required_reagents")?.let { objectMapper.readValue(it) } ?: emptyList(),
                        )
                },
                *recipeIds.toTypedArray(),
            ).toMap()
    }
}

private fun NormalizedAuctionHelperRecipe.hasCraftingRuleData(): Boolean =
    baseSkill != null ||
        baseDifficulty != null ||
        bonusSkill != null ||
        qualityThresholds.isNotEmpty() ||
        requiredReagentSkillDelta != null ||
        maxQualityRequiredReagents.isNotEmpty()
