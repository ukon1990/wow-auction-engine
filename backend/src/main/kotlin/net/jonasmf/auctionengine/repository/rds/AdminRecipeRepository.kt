package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.generated.model.AdminItemCompareField
import net.jonasmf.auctionengine.generated.model.AdminRecipe1
import net.jonasmf.auctionengine.generated.model.AdminRecipeFields
import net.jonasmf.auctionengine.generated.model.AdminRecipeOutput
import net.jonasmf.auctionengine.generated.model.AdminRecipeOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipeReagent
import net.jonasmf.auctionengine.generated.model.AdminRecipeReagentRank
import net.jonasmf.auctionengine.generated.model.PageMetadata
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.ceil

data class AdminRecipeSearchRows(
    val recipes: List<AdminRecipe1>,
    val totalItems: Long,
)

data class AdminRecipeRows(
    val effective: AdminRecipeFields,
    val base: AdminRecipeFields?,
    val override: AdminRecipeFields?,
) {
    fun toAdminRecipe(
        includeBase: Boolean,
        includeOverride: Boolean,
    ): AdminRecipe1 =
        AdminRecipe1(
            id = effective.id ?: error("Effective recipe id is missing"),
            hasBase = base != null,
            hasOverride = override != null,
            effective = effective,
            base = base.takeIf { includeBase },
            `override` = override.takeIf { includeOverride },
        )
}

interface AdminRecipeRepositoryPort {
    fun searchRecipes(
        query: String?,
        hasOverride: Boolean?,
        professionId: Int?,
        craftedItemId: Int?,
        page: Int,
        pageSize: Int,
        localeColumnSuffix: String,
    ): AdminRecipeSearchRows

    fun pageMetadata(
        page: Int,
        pageSize: Int,
        totalItems: Long,
    ): PageMetadata

    fun findRecipeRows(
        id: Int,
        localeColumnSuffix: String,
    ): AdminRecipeRows?

    fun hasBaseRecipe(id: Int): Boolean

    fun upsertOverride(
        id: Int,
        request: AdminRecipeOverrideRequest,
    )

    fun deleteOverride(id: Int): Boolean
}

@Repository
class AdminRecipeRepository(
    private val jdbcTemplate: JdbcTemplate,
) : AdminRecipeRepositoryPort {
    override fun searchRecipes(
        query: String?,
        hasOverride: Boolean?,
        professionId: Int?,
        craftedItemId: Int?,
        page: Int,
        pageSize: Int,
        localeColumnSuffix: String,
    ): AdminRecipeSearchRows {
        val params = mutableListOf<Any?>()
        val whereSql = recipeWhereSql(query, hasOverride, professionId, craftedItemId, localeColumnSuffix, params)
        val totalItems =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM v_recipe r
                    LEFT JOIN recipe override_recipe ON override_recipe.id = r.id AND override_recipe.is_override = TRUE
                    LEFT JOIN locale name_l ON name_l.id = r.name_id
                    LEFT JOIN profession_category pc ON pc.internal_id = r.profession_category_id
                    LEFT JOIN skill_tier st ON st.id = pc.skill_tier_id
                $whereSql
                """.trimIndent(),
                Long::class.java,
                *params.toTypedArray(),
            ) ?: 0

        val rows =
            jdbcTemplate.query(
                """
                ${recipeSelectSql("v_recipe", localeColumnSuffix)}
                $whereSql
                ORDER BY profession_name, skill_tier_name, profession_category_name, recipe_name, r.id
                LIMIT ? OFFSET ?
                """.trimIndent(),
                { rs, _ ->
                    AdminRecipe1(
                        id = rs.getInt("id"),
                        hasBase = rs.getBoolean("has_base"),
                        hasOverride = rs.getBoolean("has_override"),
                        effective = rs.toRecipeFields().withRecipeCollections(localeColumnSuffix),
                    )
                },
                *(params + pageSize + ((page - 1) * pageSize)).toTypedArray(),
            )
        return AdminRecipeSearchRows(rows, totalItems)
    }

    override fun pageMetadata(
        page: Int,
        pageSize: Int,
        totalItems: Long,
    ): PageMetadata =
        PageMetadata(
            page = page,
            pageSize = pageSize,
            totalItems = totalItems,
            totalPages = if (totalItems == 0L) 0 else ceil(totalItems.toDouble() / pageSize).toInt(),
        )

    override fun findRecipeRows(
        id: Int,
        localeColumnSuffix: String,
    ): AdminRecipeRows? {
        val effective =
            findFields("v_recipe", "r.id = ?", localeColumnSuffix, id)
                ?.withRecipeCollections(localeColumnSuffix)
                ?: return null
        return AdminRecipeRows(
            effective = effective,
            base =
                findFields("recipe", "r.id = ? AND r.is_override = FALSE", localeColumnSuffix, id)
                    ?.withBaseRecipeCollections(localeColumnSuffix),
            override =
                findFields("recipe", "r.id = ? AND r.is_override = TRUE", localeColumnSuffix, id)
                    ?.withOverrideRecipeCollections(localeColumnSuffix),
        )
    }

    override fun hasBaseRecipe(id: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM recipe WHERE id = ? AND is_override = FALSE",
            Long::class.java,
            id,
        )!! > 0

    fun hasOverrideRecipe(id: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM recipe WHERE id = ? AND is_override = TRUE",
            Long::class.java,
            id,
        )!! > 0

    override fun upsertOverride(
        id: Int,
        request: AdminRecipeOverrideRequest,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO recipe (
                id,
                is_override,
                crafted_item_id,
                crafted_quantity,
                rank,
                required_skill_level,
                override_note
            ) VALUES (?, TRUE, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                crafted_item_id = VALUES(crafted_item_id),
                crafted_quantity = VALUES(crafted_quantity),
                rank = VALUES(rank),
                required_skill_level = VALUES(required_skill_level),
                override_note = VALUES(override_note)
            """.trimIndent(),
            id,
            request.craftedItemId,
            request.craftedQuantity,
            request.rank,
            request.requiredSkillLevel,
            request.overrideNote,
        )
        request.outputs?.let { replaceOutputs(id, it) }
        request.reagents?.let { replaceReagents(id, it) }
    }

    override fun deleteOverride(id: Int): Boolean {
        val deletedOutputs = jdbcTemplate.update("DELETE FROM recipe_crafted_output WHERE recipe_id = ? AND is_override = TRUE", id)
        val deletedReagents = jdbcTemplate.update("DELETE FROM recipe_reagent WHERE recipe_id = ? AND is_override = TRUE", id)
        val deletedRecipe = jdbcTemplate.update("DELETE FROM recipe WHERE id = ? AND is_override = TRUE", id)
        return deletedRecipe + deletedOutputs + deletedReagents > 0
    }

    private fun replaceOutputs(
        recipeId: Int,
        outputs: List<AdminRecipeOutput>,
    ) {
        jdbcTemplate.update("DELETE FROM recipe_crafted_output WHERE recipe_id = ? AND is_override = TRUE", recipeId)
        outputs.forEachIndexed { index, output ->
            jdbcTemplate.update(
                """
                INSERT INTO recipe_crafted_output (
                    recipe_id, sort_order, is_override, crafted_item_id, crafted_quantity, required_skill_level
                ) VALUES (?, ?, TRUE, ?, ?, ?)
                """.trimIndent(),
                recipeId,
                output.sortOrder.takeIf { it >= 0 } ?: index,
                output.craftedItemId,
                output.craftedQuantity,
                output.requiredSkillLevel,
            )
        }
    }

    private fun replaceReagents(
        recipeId: Int,
        reagents: List<AdminRecipeReagent>,
    ) {
        jdbcTemplate.update("DELETE FROM recipe_reagent WHERE recipe_id = ? AND is_override = TRUE", recipeId)
        reagents.forEachIndexed { index, reagent ->
            jdbcTemplate.update(
                """
                INSERT INTO recipe_reagent (item_id, quantity, recipe_id, sort_order, is_override)
                VALUES (?, ?, ?, ?, TRUE)
                """.trimIndent(),
                reagent.itemId,
                reagent.quantity,
                recipeId,
                reagent.sortOrder.takeIf { it >= 0 } ?: index,
            )
            val reagentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
            reagent.ranks.orEmpty().forEach { rank ->
                jdbcTemplate.update(
                    """
                    INSERT INTO recipe_reagent_rank (recipe_reagent_id, rank, is_override, item_id, skill_points)
                    VALUES (?, ?, TRUE, ?, ?)
                    """.trimIndent(),
                    reagentId,
                    rank.rank,
                    rank.itemId,
                    rank.skillPoints,
                )
            }
        }
    }

    private fun findFields(
        source: String,
        predicate: String,
        localeColumnSuffix: String,
        id: Int,
    ): AdminRecipeFields? =
        jdbcTemplate
            .query(
                """
                ${recipeSelectSql(source, localeColumnSuffix)}
                WHERE $predicate
                """.trimIndent(),
                { rs, _ -> rs.toRecipeFields() },
                id,
            ).firstOrNull()

    private fun AdminRecipeFields.withRecipeCollections(localeColumnSuffix: String): AdminRecipeFields =
        copy(
            outputs = outputs(id ?: return this, "v_recipe_crafted_output", localeColumnSuffix),
            reagents = reagents(id, "v_recipe_reagent", localeColumnSuffix),
        )

    private fun AdminRecipeFields.withBaseRecipeCollections(localeColumnSuffix: String): AdminRecipeFields =
        copy(
            outputs = outputs(id ?: return this, "recipe", localeColumnSuffix),
            reagents = reagents(id, "recipe_reagent", localeColumnSuffix, isOverride = false),
        )

    private fun AdminRecipeFields.withOverrideRecipeCollections(localeColumnSuffix: String): AdminRecipeFields =
        copy(
            outputs = outputs(id ?: return this, "recipe_crafted_output", localeColumnSuffix, isOverride = true),
            reagents = reagents(id, "recipe_reagent", localeColumnSuffix, isOverride = true),
        )

    private fun outputs(
        recipeId: Int,
        source: String,
        localeColumnSuffix: String,
        isOverride: Boolean? = null,
    ): List<AdminRecipeOutput> {
        val (fromSql, whereSql) =
            if (source == "recipe") {
                "recipe o" to "o.id = ? AND o.is_override = FALSE AND o.crafted_item_id IS NOT NULL"
            } else {
                "$source o" to
                    buildString {
                        append("o.recipe_id = ?")
                        isOverride?.let { append(" AND o.is_override = $it") }
                    }
            }
        return jdbcTemplate.query(
            """
            SELECT
                ${if (source == "recipe") "NULL" else "o.id"} AS id,
                ${if (source == "recipe") "0" else "o.sort_order"} AS sort_order,
                ${if (source == "recipe") "o.crafted_item_id" else "o.crafted_item_id"} AS crafted_item_id,
                COALESCE(NULLIF(o.crafted_quantity, 0), 1) AS crafted_quantity,
                o.required_skill_level,
                COALESCE(item_l.$localeColumnSuffix, item_l.en_gb, item_l.en_us, CAST(o.crafted_item_id AS CHAR)) AS item_name
            FROM $fromSql
                LEFT JOIN v_item item ON item.id = o.crafted_item_id
                LEFT JOIN locale item_l ON item_l.id = item.name_id
            WHERE $whereSql
            ORDER BY sort_order, crafted_item_id
            """.trimIndent(),
            { rs, _ ->
                AdminRecipeOutput(
                    id = rs.nullableLong("id"),
                    sortOrder = rs.getInt("sort_order"),
                    craftedItemId = rs.getInt("crafted_item_id"),
                    craftedItemName = rs.getString("item_name"),
                    craftedQuantity = rs.getInt("crafted_quantity"),
                    requiredSkillLevel = rs.nullableInt("required_skill_level"),
                )
            },
            recipeId,
        )
    }

    private fun reagents(
        recipeId: Int,
        source: String,
        localeColumnSuffix: String,
        isOverride: Boolean? = null,
    ): List<AdminRecipeReagent> {
        val whereSql =
            buildString {
                append("rr.recipe_id = ?")
                isOverride?.let { append(" AND rr.is_override = $it") }
            }
        val rows =
            jdbcTemplate.query(
                """
                SELECT
                    rr.internal_id,
                    rr.item_id,
                    rr.quantity,
                    rr.sort_order,
                    COALESCE(item_l.$localeColumnSuffix, item_l.en_gb, item_l.en_us, reagent_l.$localeColumnSuffix, reagent_l.en_gb, reagent_l.en_us, CAST(rr.item_id AS CHAR)) AS item_name
                FROM $source rr
                    LEFT JOIN v_item item ON item.id = rr.item_id
                    LEFT JOIN locale item_l ON item_l.id = item.name_id
                    LEFT JOIN locale reagent_l ON reagent_l.id = rr.name_id
                WHERE $whereSql
                ORDER BY rr.sort_order, rr.internal_id
                """.trimIndent(),
                { rs, _ ->
                    AdminRecipeReagent(
                        id = rs.getLong("internal_id"),
                        itemId = rs.getInt("item_id"),
                        itemName = rs.getString("item_name"),
                        quantity = rs.getInt("quantity"),
                        sortOrder = rs.getInt("sort_order"),
                        ranks = emptyList(),
                    )
                },
                recipeId,
            )
        if (rows.isEmpty()) return rows
        val ranksByReagent = ranks(rows.mapNotNull(AdminRecipeReagent::id))
        return rows.map { reagent -> reagent.copy(ranks = ranksByReagent[reagent.id].orEmpty()) }
    }

    private fun ranks(reagentIds: List<Long>): Map<Long, List<AdminRecipeReagentRank>> {
        if (reagentIds.isEmpty()) return emptyMap()
        val placeholders = reagentIds.joinToString(",") { "?" }
        return jdbcTemplate
            .query(
                """
                SELECT recipe_reagent_id, rank, item_id, skill_points
                FROM recipe_reagent_rank
                WHERE recipe_reagent_id IN ($placeholders)
                ORDER BY recipe_reagent_id, rank
                """.trimIndent(),
                { rs, _ ->
                    rs.getLong("recipe_reagent_id") to
                        AdminRecipeReagentRank(
                            rank = rs.getInt("rank"),
                            itemId = rs.getInt("item_id"),
                            skillPoints = rs.nullableInt("skill_points"),
                        )
                },
                *reagentIds.toTypedArray(),
            ).groupBy({ it.first }, { it.second })
    }
}

private fun recipeSelectSql(
    source: String,
    localeColumnSuffix: String,
): String =
    """
    SELECT
        r.id,
        base_recipe.id IS NOT NULL AS has_base,
        override_recipe.id IS NOT NULL AS has_override,
        COALESCE(name_l.$localeColumnSuffix, name_l.en_gb, name_l.en_us, CAST(r.id AS CHAR)) AS recipe_name,
        COALESCE(p_l.$localeColumnSuffix, p_l.en_gb, p_l.en_us, CAST(p.id AS CHAR), 'Unknown profession') AS profession_name,
        COALESCE(st_l.$localeColumnSuffix, st_l.en_gb, st_l.en_us, CAST(st.id AS CHAR), 'Unknown skill tier') AS skill_tier_name,
        COALESCE(pc_l.$localeColumnSuffix, pc_l.en_gb, pc_l.en_us, CAST(pc.internal_id AS CHAR), 'Unknown category') AS profession_category_name,
        r.crafted_item_id,
        COALESCE(item_l.$localeColumnSuffix, item_l.en_gb, item_l.en_us, CAST(r.crafted_item_id AS CHAR)) AS crafted_item_name,
        r.crafted_quantity,
        r.rank,
        r.required_skill_level,
        r.media_url,
        r.media_source_url,
        r.override_note,
        r.created_at,
        r.updated_at
    FROM $source r
        LEFT JOIN recipe base_recipe ON base_recipe.id = r.id AND base_recipe.is_override = FALSE
        LEFT JOIN recipe override_recipe ON override_recipe.id = r.id AND override_recipe.is_override = TRUE
        LEFT JOIN locale name_l ON name_l.id = r.name_id
        LEFT JOIN profession_category pc ON pc.internal_id = r.profession_category_id
        LEFT JOIN locale pc_l ON pc_l.id = pc.name_id
        LEFT JOIN skill_tier st ON st.id = pc.skill_tier_id
        LEFT JOIN locale st_l ON st_l.id = st.name_id
        LEFT JOIN profession p ON p.id = st.profession_id
        LEFT JOIN locale p_l ON p_l.id = p.name_id
        LEFT JOIN v_item crafted_item ON crafted_item.id = r.crafted_item_id
        LEFT JOIN locale item_l ON item_l.id = crafted_item.name_id
    """.trimIndent()

private fun recipeWhereSql(
    query: String?,
    hasOverride: Boolean?,
    professionId: Int?,
    craftedItemId: Int?,
    localeColumnSuffix: String,
    params: MutableList<Any?>,
): String {
    val clauses = mutableListOf<String>()
    query?.trim()?.takeIf(String::isNotEmpty)?.let { term ->
        clauses +=
            """
            (
                CAST(r.id AS CHAR) LIKE ?
                OR name_l.$localeColumnSuffix LIKE ?
                OR name_l.en_gb LIKE ?
                OR name_l.en_us LIKE ?
            )
            """.trimIndent()
        val like = "%$term%"
        repeat(4) { params += like }
    }
    hasOverride?.let {
        clauses += if (it) "override_recipe.id IS NOT NULL" else "override_recipe.id IS NULL"
    }
    professionId?.let {
        clauses += "st.profession_id = ?"
        params += it
    }
    craftedItemId?.let {
        clauses += "EXISTS (SELECT 1 FROM v_recipe_crafted_output o WHERE o.recipe_id = r.id AND o.crafted_item_id = ?)"
        params += it
    }
    return if (clauses.isEmpty()) "" else clauses.joinToString(prefix = "WHERE ", separator = "\n  AND ")
}

private fun ResultSet.toRecipeFields(): AdminRecipeFields =
    AdminRecipeFields(
        id = getInt("id"),
        name = getString("recipe_name"),
        professionName = getString("profession_name"),
        skillTierName = getString("skill_tier_name"),
        professionCategoryName = getString("profession_category_name"),
        craftedItemId = nullableInt("crafted_item_id"),
        craftedItemName = getString("crafted_item_name"),
        craftedQuantity = nullableInt("crafted_quantity"),
        rank = nullableInt("rank"),
        requiredSkillLevel = nullableInt("required_skill_level"),
        mediaUrl = getString("media_url"),
        mediaSourceUrl = getString("media_source_url"),
        outputs = emptyList(),
        reagents = emptyList(),
        overrideNote = getString("override_note"),
        createdAt = nullableTimestamp("created_at")?.toOffsetDateTime(),
        updatedAt = nullableTimestamp("updated_at")?.toOffsetDateTime(),
    )

private fun ResultSet.nullableInt(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

private fun ResultSet.nullableLong(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

private fun ResultSet.nullableTimestamp(column: String): Timestamp? = getTimestamp(column)?.takeUnless { wasNull() }

private fun Timestamp.toOffsetDateTime(): OffsetDateTime = toInstant().atOffset(ZoneOffset.UTC)
