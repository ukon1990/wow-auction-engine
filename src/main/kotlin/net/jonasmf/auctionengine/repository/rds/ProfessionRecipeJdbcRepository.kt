package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingSlot
import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.ProfessionCategory
import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.domain.profession.RecipeReagent
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.LocaleDTO
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.simple.SimpleJdbcInsert
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

private const val PROFESSION_RECIPE_JDBC_CHUNK_SIZE = 500

@Repository
class ProfessionRecipeJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val localeInsert = SimpleJdbcInsert(jdbcTemplate).withTableName("locale").usingGeneratedKeyColumns("id")
    private val categoryInsert =
        SimpleJdbcInsert(
            jdbcTemplate,
        ).withTableName("profession_category").usingGeneratedKeyColumns("internal_id")
    private val slotInsert =
        SimpleJdbcInsert(
            jdbcTemplate,
        ).withTableName("modified_crafting_slot").usingGeneratedKeyColumns("internal_id")
    private val slotCategoryInsert =
        SimpleJdbcInsert(
            jdbcTemplate,
        ).withTableName("modified_crafting_category").usingGeneratedKeyColumns("internal_id")

    @Transactional
    fun syncProfessionSkillTier(
        profession: Profession,
        skillTier: SkillTier,
    ) {
        upsertProfession(profession)
        upsertSkillTier(profession.id, skillTier)

        val oldCategoryIds = findCategoryIds(skillTier.id)
        val oldRecipeIds = findRecipeIdsByCategoryIds(oldCategoryIds)

        val currentRecipeIds =
            skillTier.categories
                .flatMap(ProfessionCategory::recipes)
                .map(Recipe::id)
                .toSet()
        val retainedCategoryIds = upsertCategoriesAndUpsertRecipes(skillTier.id, skillTier.categories)

        val removedRecipeIds = oldRecipeIds - currentRecipeIds
        deleteRecipes(removedRecipeIds)
        deleteCategories(oldCategoryIds - retainedCategoryIds)
    }

    private fun upsertProfession(profession: Profession) {
        val sourceKey = localeSourceKey(profession.id)
        val nameId = upsertLocale(LocaleSourceType.PROFESSION, sourceKey, "name", profession.name)
        val descriptionId = upsertLocale(LocaleSourceType.PROFESSION, sourceKey, "description", profession.description)

        jdbcTemplate.update(
            """
            INSERT INTO profession (id, name_id, description_id, media_url, last_modified)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name_id = VALUES(name_id),
                description_id = VALUES(description_id),
                media_url = VALUES(media_url),
                last_modified = VALUES(last_modified)
            """.trimIndent(),
            profession.id,
            nameId,
            descriptionId,
            profession.mediaUrl,
            profession.lastModified?.let(Timestamp::from),
        )
    }

    private fun upsertSkillTier(
        professionId: Int,
        skillTier: SkillTier,
    ) {
        val nameId = upsertLocale(LocaleSourceType.SKILL_TIER, localeSourceKey(skillTier.id), "name", skillTier.name)

        jdbcTemplate.update(
            """
            INSERT INTO skill_tier (id, name_id, minimum_skill_level, maximum_skill_level, profession_id)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name_id = VALUES(name_id),
                minimum_skill_level = VALUES(minimum_skill_level),
                maximum_skill_level = VALUES(maximum_skill_level),
                profession_id = VALUES(profession_id)
            """.trimIndent(),
            skillTier.id,
            nameId,
            skillTier.minimumSkillLevel,
            skillTier.maximumSkillLevel,
            professionId,
        )
    }

    private data class ExistingCategory(
        val internalId: Long,
        val localeId: Long,
    )

    private fun upsertCategoriesAndUpsertRecipes(
        skillTierId: Int,
        categories: List<ProfessionCategory>,
    ): Set<Long> {
        val existingCategories = findExistingCategories(skillTierId)
        val retainedCategoryIds = mutableSetOf<Long>()

        categories.forEachIndexed { index, category ->
            val existingCategory = existingCategories.getOrNull(index)
            val categoryId =
                if (existingCategory != null) {
                    updateLocale(
                        existingCategory.localeId,
                        LocaleSourceType.PROFESSION_CATEGORY,
                        localeSourceKey(skillTierId, index),
                        "name",
                        category.name,
                    )
                    existingCategory.internalId
                } else {
                    val categoryLocaleId =
                        insertLocale(
                            LocaleSourceType.PROFESSION_CATEGORY,
                            localeSourceKey(skillTierId, index),
                            "name",
                            category.name,
                        )
                    categoryInsert
                        .executeAndReturnKey(
                            MapSqlParameterSource()
                                .addValue("name_id", categoryLocaleId)
                                .addValue("skill_tier_id", skillTierId),
                        ).toLong()
                }
            retainedCategoryIds += categoryId
            category.recipes.forEach { recipe -> upsertRecipe(categoryId, recipe) }
        }

        return retainedCategoryIds
    }

    private fun upsertRecipe(
        categoryId: Long,
        recipe: Recipe,
    ) {
        val existingDescriptionId =
            jdbcTemplate
                .query(
                    "SELECT description_id FROM recipe WHERE id = ?",
                    { rs, _ -> rs.getLong("description_id").takeIf { !rs.wasNull() } },
                    recipe.id,
                ).firstOrNull()

        val sourceKey = localeSourceKey(recipe.id)
        val nameId = upsertLocale(LocaleSourceType.RECIPE, sourceKey, "name", recipe.name)
        val descriptionId =
            recipe.description?.let {
                upsertLocale(
                    LocaleSourceType.RECIPE,
                    sourceKey,
                    "description",
                    it,
                )
            }

        jdbcTemplate.update(
            """
            INSERT INTO recipe (
                id,
                name_id,
                description_id,
                media_url,
                last_modified,
                rank,
                crafted_item_id,
                crafted_quantity,
                profession_category_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name_id = VALUES(name_id),
                description_id = VALUES(description_id),
                media_url = VALUES(media_url),
                last_modified = VALUES(last_modified),
                rank = VALUES(rank),
                crafted_item_id = VALUES(crafted_item_id),
                crafted_quantity = VALUES(crafted_quantity),
                profession_category_id = VALUES(profession_category_id)
            """.trimIndent(),
            recipe.id,
            nameId,
            descriptionId,
            recipe.mediaUrl,
            recipe.lastModified?.let(Timestamp::from),
            recipe.rank,
            recipe.craftedItemId,
            recipe.craftedQuantity,
            categoryId,
        )

        if (descriptionId == null && existingDescriptionId != null) {
            deleteLocaleIds(setOf(existingDescriptionId))
        }

        replaceRecipeReagents(recipe.id, recipe.reagents)
        replaceRecipeModifiedCraftingSlots(recipe.id, recipe.modifiedCraftingSlots)
    }

    private fun replaceRecipeReagents(
        recipeId: Int,
        reagents: List<RecipeReagent>,
    ) {
        jdbcTemplate.update("DELETE FROM recipe_reagent WHERE recipe_id = ?", recipeId)
        reagents.forEachIndexed { index, reagent ->
            val localeId =
                upsertLocale(
                    LocaleSourceType.RECIPE_REAGENT,
                    localeSourceKey(recipeId, index, reagent.itemId),
                    "name",
                    reagent.name,
                )
            jdbcTemplate.update(
                "INSERT INTO recipe_reagent (item_id, name_id, quantity, recipe_id) VALUES (?, ?, ?, ?)",
                reagent.itemId,
                localeId,
                reagent.quantity,
                recipeId,
            )
        }
    }

    private fun replaceRecipeModifiedCraftingSlots(
        recipeId: Int,
        slots: List<ModifiedCraftingSlot>,
    ) {
        val existingSlotIds =
            jdbcTemplate.query(
                "SELECT internal_id FROM modified_crafting_slot WHERE recipe_id = ?",
                { rs, _ -> rs.getLong("internal_id") },
                recipeId,
            )
        existingSlotIds.forEach { slotId ->
            jdbcTemplate.update("DELETE FROM modified_crafting_category WHERE modified_crafting_slot_id = ?", slotId)
        }
        jdbcTemplate.update("DELETE FROM modified_crafting_slot WHERE recipe_id = ?", recipeId)

        slots.forEachIndexed { slotIndex, slot ->
            val slotLocaleId =
                upsertLocale(
                    LocaleSourceType.MODIFIED_CRAFTING_SLOT,
                    localeSourceKey(recipeId, slotIndex, slot.id),
                    "description",
                    slot.description,
                )
            val slotId =
                slotInsert
                    .executeAndReturnKey(
                        MapSqlParameterSource()
                            .addValue("slot_type_id", slot.id)
                            .addValue("description_id", slotLocaleId)
                            .addValue("display_order", slot.displayOrder)
                            .addValue("recipe_id", recipeId),
                    ).toLong()
            slot.compatibleCategories.forEach { category ->
                val categoryLocaleId =
                    upsertLocale(
                        LocaleSourceType.MODIFIED_CRAFTING_CATEGORY,
                        localeSourceKey(recipeId, slotIndex, slot.id, category.id),
                        "name",
                        category.name,
                    )
                slotCategoryInsert.execute(
                    MapSqlParameterSource()
                        .addValue("category_id", category.id)
                        .addValue("name_id", categoryLocaleId)
                        .addValue("modified_crafting_slot_id", slotId),
                )
            }
        }
    }

    private fun findCategoryIds(skillTierId: Int): Set<Long> =
        jdbcTemplate
            .query(
                "SELECT internal_id FROM profession_category WHERE skill_tier_id = ?",
                { rs, _ -> rs.getLong("internal_id") },
                skillTierId,
            ).toSet()

    private fun findExistingCategories(skillTierId: Int): List<ExistingCategory> =
        jdbcTemplate.query(
            "SELECT internal_id, name_id FROM profession_category WHERE skill_tier_id = ? ORDER BY internal_id",
            { rs, _ -> ExistingCategory(rs.getLong("internal_id"), rs.getLong("name_id")) },
            skillTierId,
        )

    private fun findRecipeIdsByCategoryIds(categoryIds: Set<Long>): Set<Int> {
        if (categoryIds.isEmpty()) return emptySet()
        val placeholders = categoryIds.joinToString(",") { "?" }
        return jdbcTemplate
            .query(
                "SELECT id FROM recipe WHERE profession_category_id IN ($placeholders)",
                { rs, _ -> rs.getInt("id") },
                *categoryIds.toTypedArray(),
            ).toSet()
    }

    private fun deleteRecipes(recipeIds: Set<Int>) {
        if (recipeIds.isEmpty()) return
        recipeIds.chunked(PROFESSION_RECIPE_JDBC_CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val existingSlotIds =
                jdbcTemplate.query(
                    "SELECT internal_id FROM modified_crafting_slot WHERE recipe_id IN ($placeholders)",
                    { rs, _ -> rs.getLong("internal_id") },
                    *chunk.toTypedArray(),
                )
            val localeIds = mutableSetOf<Long>()
            localeIds +=
                jdbcTemplate.query(
                    "SELECT name_id FROM recipe_reagent WHERE recipe_id IN ($placeholders)",
                    { rs, _ -> rs.getLong("name_id") },
                    *chunk.toTypedArray(),
                )
            localeIds +=
                jdbcTemplate.query(
                    "SELECT description_id FROM modified_crafting_slot WHERE recipe_id IN ($placeholders)",
                    { rs, _ -> rs.getLong("description_id") },
                    *chunk.toTypedArray(),
                )
            if (existingSlotIds.isNotEmpty()) {
                val slotPlaceholders = existingSlotIds.joinToString(",") { "?" }
                localeIds +=
                    jdbcTemplate.query(
                        "SELECT name_id FROM modified_crafting_category WHERE modified_crafting_slot_id IN ($slotPlaceholders)",
                        { rs, _ -> rs.getLong("name_id") },
                        *existingSlotIds.toTypedArray(),
                    )
                existingSlotIds.forEach { slotId ->
                    jdbcTemplate.update(
                        "DELETE FROM modified_crafting_category WHERE modified_crafting_slot_id = ?",
                        slotId,
                    )
                }
            }
            localeIds +=
                jdbcTemplate.query(
                    "SELECT name_id FROM recipe WHERE id IN ($placeholders)",
                    { rs, _ -> rs.getLong("name_id") },
                    *chunk.toTypedArray(),
                )
            localeIds +=
                jdbcTemplate.query(
                    "SELECT description_id FROM recipe WHERE id IN ($placeholders) AND description_id IS NOT NULL",
                    { rs, _ -> rs.getLong("description_id") },
                    *chunk.toTypedArray(),
                )
            jdbcTemplate.update(
                "DELETE FROM modified_crafting_slot WHERE recipe_id IN ($placeholders)",
                *chunk.toTypedArray(),
            )
            jdbcTemplate.update("DELETE FROM recipe_reagent WHERE recipe_id IN ($placeholders)", *chunk.toTypedArray())
            jdbcTemplate.update("DELETE FROM recipe WHERE id IN ($placeholders)", *chunk.toTypedArray())
            deleteLocaleIds(localeIds)
        }
    }

    private fun deleteCategories(categoryIds: Set<Long>) {
        if (categoryIds.isEmpty()) return
        categoryIds.chunked(PROFESSION_RECIPE_JDBC_CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val localeIds =
                jdbcTemplate
                    .query(
                        "SELECT name_id FROM profession_category WHERE internal_id IN ($placeholders)",
                        { rs, _ -> rs.getLong("name_id") },
                        *chunk.toTypedArray(),
                    ).toSet()
            jdbcTemplate.update(
                "DELETE FROM profession_category WHERE internal_id IN ($placeholders)",
                *chunk.toTypedArray(),
            )
            deleteLocaleIds(localeIds)
        }
    }

    private fun findLocaleId(
        sourceType: String,
        sourceKey: String,
        sourceField: String,
    ): Long? =
        jdbcTemplate
            .query(
                "SELECT id FROM locale WHERE source_type = ? AND source_key = ? AND source_field = ?",
                { rs, _ -> rs.getLong("id") },
                sourceType,
                sourceKey,
                sourceField,
            ).firstOrNull()

    private fun upsertLocale(
        sourceType: String,
        sourceKey: String,
        sourceField: String,
        locale: LocaleDTO,
    ): Long {
        val existingId = findLocaleId(sourceType, sourceKey, sourceField)
        if (existingId != null) {
            updateLocale(existingId, sourceType, sourceKey, sourceField, locale)
            return existingId
        }
        return insertLocale(sourceType, sourceKey, sourceField, locale)
    }

    private fun insertLocale(
        sourceType: String,
        sourceKey: String,
        sourceField: String,
        locale: LocaleDTO,
    ): Long =
        localeInsert
            .executeAndReturnKey(
                MapSqlParameterSource()
                    .addValue("source_type", sourceType)
                    .addValue("source_key", sourceKey)
                    .addValue("source_field", sourceField)
                    .addValue("en_us", locale.en_US)
                    .addValue("es_mx", locale.es_MX)
                    .addValue("pt_br", locale.pt_BR)
                    .addValue("pt_pt", locale.pt_PT)
                    .addValue("de_de", locale.de_DE)
                    .addValue("en_gb", locale.en_GB)
                    .addValue("es_es", locale.es_ES)
                    .addValue("fr_fr", locale.fr_FR)
                    .addValue("it_it", locale.it_IT)
                    .addValue("ru_ru", locale.ru_RU)
                    .addValue("ko_kr", locale.ko_KR)
                    .addValue("zh_tw", locale.zh_TW)
                    .addValue("zh_cn", locale.zh_CN),
            ).toLong()

    private fun updateLocale(
        id: Long,
        sourceType: String,
        sourceKey: String,
        sourceField: String,
        locale: LocaleDTO,
    ) {
        jdbcTemplate.update(
            """
            UPDATE locale
            SET source_type = ?, source_key = ?, source_field = ?,
                en_us = ?, es_mx = ?, pt_br = ?, pt_pt = ?, de_de = ?, en_gb = ?, es_es = ?, fr_fr = ?,
                it_it = ?, ru_ru = ?, ko_kr = ?, zh_tw = ?, zh_cn = ?
            WHERE id = ?
            """.trimIndent(),
            sourceType,
            sourceKey,
            sourceField,
            locale.en_US,
            locale.es_MX,
            locale.pt_BR,
            locale.pt_PT,
            locale.de_DE,
            locale.en_GB,
            locale.es_ES,
            locale.fr_FR,
            locale.it_IT,
            locale.ru_RU,
            locale.ko_KR,
            locale.zh_TW,
            locale.zh_CN,
            id,
        )
    }

    private fun deleteLocaleIds(localeIds: Set<Long>) {
        if (localeIds.isEmpty()) return
        localeIds.chunked(PROFESSION_RECIPE_JDBC_CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            jdbcTemplate.update("DELETE FROM locale WHERE id IN ($placeholders)", *chunk.toTypedArray())
        }
    }
}
