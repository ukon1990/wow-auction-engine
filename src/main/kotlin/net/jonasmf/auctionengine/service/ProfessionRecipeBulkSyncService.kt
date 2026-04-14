package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingCategory
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingSlot
import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.ProfessionCategory
import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.repository.rds.ProfessionRecipeJdbcRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.simple.SimpleJdbcInsert
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ProfessionRecipePersistenceSummary(
    val professionsUpserted: Int,
    val skillTiersUpserted: Int,
    val categoriesReplaced: Int,
    val recipesUpserted: Int,
    val reagentsReplaced: Int,
    val recipeSlotsReplaced: Int,
    val modifiedCraftingCategoriesUpserted: Int,
    val modifiedCraftingSlotsUpserted: Int,
    val slotCategoryLinksReplaced: Int,
)

private const val MODIFIED_CRAFTING_METADATA_JDBC_CHUNK_SIZE = 500

@Service
class ProfessionRecipeBulkSyncService(
    private val professionRecipeJdbcRepository: ProfessionRecipeJdbcRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(ProfessionRecipeBulkSyncService::class.java)
    private val categoryMetadataLocaleTableName by lazy {
        resolveReferencedLocaleTable(
            tableName = "modified_crafting_category_metadata",
            columnName = "name_id",
        )
    }
    private val slotMetadataLocaleTableName by lazy {
        resolveReferencedLocaleTable(
            tableName = "modified_crafting_slot_metadata",
            columnName = "description_id",
        )
    }

    @Transactional
    fun sync(
        professions: List<Profession>,
        recipes: List<Recipe>,
        modifiedCraftingCategories: List<ModifiedCraftingCategory>,
        modifiedCraftingSlots: List<ModifiedCraftingSlot>,
    ): ProfessionRecipePersistenceSummary {
        syncModifiedCraftingMetadata(modifiedCraftingCategories, modifiedCraftingSlots)

        var professionsUpserted = 0
        var skillTiersUpserted = 0
        var categoriesReplaced = 0
        var recipesUpserted = 0
        var reagentsReplaced = 0
        var recipeSlotsReplaced = 0

        professions.forEach { profession ->
            profession.skillTiers.forEach { skillTier ->
                val summary = syncProfessionSkillTier(profession.copy(skillTiers = emptyList()), skillTier, recipes, modifiedCraftingSlots)
                professionsUpserted += summary.professionsUpserted
                skillTiersUpserted += summary.skillTiersUpserted
                categoriesReplaced += summary.categoriesReplaced
                recipesUpserted += summary.recipesUpserted
                reagentsReplaced += summary.reagentsReplaced
                recipeSlotsReplaced += summary.recipeSlotsReplaced
            }
        }

        return ProfessionRecipePersistenceSummary(
            professionsUpserted = professionsUpserted,
            skillTiersUpserted = skillTiersUpserted,
            categoriesReplaced = categoriesReplaced,
            recipesUpserted = recipesUpserted,
            reagentsReplaced = reagentsReplaced,
            recipeSlotsReplaced = recipeSlotsReplaced,
            modifiedCraftingCategoriesUpserted = modifiedCraftingCategories.size,
            modifiedCraftingSlotsUpserted = modifiedCraftingSlots.size,
            slotCategoryLinksReplaced = modifiedCraftingSlots.sumOf { it.compatibleCategories.size },
        )
    }

    @Transactional
    fun syncModifiedCraftingMetadata(
        modifiedCraftingCategories: List<ModifiedCraftingCategory>,
        modifiedCraftingSlots: List<ModifiedCraftingSlot>,
    ) {
        upsertModifiedCraftingMetadata(modifiedCraftingCategories, modifiedCraftingSlots)
    }

    @Transactional
    fun syncProfessionSkillTier(
        profession: Profession,
        skillTier: SkillTier,
        recipes: List<Recipe>,
        modifiedCraftingSlots: List<ModifiedCraftingSlot>,
    ): ProfessionRecipePersistenceSummary {
        val recipesById = recipes.associateBy { it.id }
        val slotsById = modifiedCraftingSlots.associateBy { it.id }
        val enrichedSkillTier = skillTier.resolveRecipeDetails(recipesById, slotsById)
        professionRecipeJdbcRepository.syncProfessionSkillTier(profession, enrichedSkillTier)

        val enrichedRecipes = enrichedSkillTier.categories.flatMap(ProfessionCategory::recipes)
        val summary =
            ProfessionRecipePersistenceSummary(
                professionsUpserted = 1,
                skillTiersUpserted = 1,
                categoriesReplaced = enrichedSkillTier.categories.size,
                recipesUpserted = enrichedRecipes.size,
                reagentsReplaced = enrichedRecipes.sumOf { it.reagents.size },
                recipeSlotsReplaced = enrichedRecipes.sumOf { it.modifiedCraftingSlots.size },
                modifiedCraftingCategoriesUpserted = 0,
                modifiedCraftingSlotsUpserted = 0,
                slotCategoryLinksReplaced = 0,
            )

        log.info(
            "Persisted profession skill tier summary professionId={} skillTierId={} categories={} recipes={} reagents={} recipeSlots={}",
            profession.id,
            enrichedSkillTier.id,
            summary.categoriesReplaced,
            summary.recipesUpserted,
            summary.reagentsReplaced,
            summary.recipeSlotsReplaced,
        )

        return summary
    }

    private fun upsertModifiedCraftingMetadata(
        modifiedCraftingCategories: List<ModifiedCraftingCategory>,
        modifiedCraftingSlots: List<ModifiedCraftingSlot>,
    ) {
        val currentCategoryIds = modifiedCraftingCategories.map(ModifiedCraftingCategory::id).toSet()
        val currentSlotIds = modifiedCraftingSlots.map(ModifiedCraftingSlot::id).toSet()

        modifiedCraftingCategories.forEach(::upsertModifiedCraftingCategoryMetadata)
        modifiedCraftingSlots.forEach(::upsertModifiedCraftingSlotMetadata)

        deleteStaleModifiedCraftingSlotMetadata(currentSlotIds)
        deleteStaleModifiedCraftingCategoryMetadata(currentCategoryIds)
    }

    private fun upsertModifiedCraftingCategoryMetadata(category: ModifiedCraftingCategory) {
        val nameId =
            upsertLocale(
                localeTableName = categoryMetadataLocaleTableName,
                sourceType = LocaleSourceType.MODIFIED_CRAFTING_CATEGORY_METADATA,
                sourceKey = localeSourceKey(category.id),
                sourceField = "name",
                locale = category.name,
            )

        jdbcTemplate.update(
            """
            INSERT INTO modified_crafting_category_metadata (id, name_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE
                name_id = VALUES(name_id)
            """.trimIndent(),
            category.id,
            nameId,
        )
    }

    private fun upsertModifiedCraftingSlotMetadata(slot: ModifiedCraftingSlot) {
        val descriptionId =
            upsertLocale(
                localeTableName = slotMetadataLocaleTableName,
                sourceType = LocaleSourceType.MODIFIED_CRAFTING_SLOT_METADATA,
                sourceKey = localeSourceKey(slot.id),
                sourceField = "description",
                locale = slot.description,
            )

        jdbcTemplate.update(
            """
            INSERT INTO modified_crafting_slot_metadata (id, description_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE
                description_id = VALUES(description_id)
            """.trimIndent(),
            slot.id,
            descriptionId,
        )

        jdbcTemplate.update("DELETE FROM modified_crafting_slot_metadata_category WHERE slot_id = ?", slot.id)
        slot.compatibleCategories.forEach { category ->
            jdbcTemplate.update(
                "INSERT INTO modified_crafting_slot_metadata_category (slot_id, category_id) VALUES (?, ?)",
                slot.id,
                category.id,
            )
        }
    }

    private fun deleteStaleModifiedCraftingCategoryMetadata(currentCategoryIds: Set<Int>) {
        val staleCategoryIds = findStaleIds("modified_crafting_category_metadata", currentCategoryIds)
        if (staleCategoryIds.isEmpty()) return

        staleCategoryIds.chunked(MODIFIED_CRAFTING_METADATA_JDBC_CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val localeIds =
                jdbcTemplate
                    .query(
                        "SELECT name_id FROM modified_crafting_category_metadata WHERE id IN ($placeholders)",
                        { rs, _ -> rs.getLong("name_id") },
                        *chunk.toTypedArray(),
                    ).toSet()
            jdbcTemplate.update(
                "DELETE FROM modified_crafting_category_metadata WHERE id IN ($placeholders)",
                *chunk.toTypedArray(),
            )
            deleteLocaleIds(localeIds, categoryMetadataLocaleTableName)
        }
    }

    private fun deleteStaleModifiedCraftingSlotMetadata(currentSlotIds: Set<Int>) {
        val staleSlotIds = findStaleIds("modified_crafting_slot_metadata", currentSlotIds)
        if (staleSlotIds.isEmpty()) return

        staleSlotIds.chunked(MODIFIED_CRAFTING_METADATA_JDBC_CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val localeIds =
                jdbcTemplate
                    .query(
                        "SELECT description_id FROM modified_crafting_slot_metadata WHERE id IN ($placeholders)",
                        { rs, _ -> rs.getLong("description_id") },
                        *chunk.toTypedArray(),
                    ).toSet()
            jdbcTemplate.update(
                "DELETE FROM modified_crafting_slot_metadata_category WHERE slot_id IN ($placeholders)",
                *chunk.toTypedArray(),
            )
            jdbcTemplate.update(
                "DELETE FROM modified_crafting_slot_metadata WHERE id IN ($placeholders)",
                *chunk.toTypedArray(),
            )
            deleteLocaleIds(localeIds, slotMetadataLocaleTableName)
        }
    }

    private fun findStaleIds(
        tableName: String,
        currentIds: Set<Int>,
    ): Set<Int> {
        val existingIds =
            jdbcTemplate
                .query(
                    "SELECT id FROM $tableName",
                    { rs, _ -> rs.getInt("id") },
                ).toSet()
        return existingIds - currentIds
    }

    private fun findLocaleId(
        localeTableName: String,
        sourceType: String,
        sourceKey: String,
        sourceField: String,
    ): Long? =
        jdbcTemplate
            .query(
                "SELECT id FROM $localeTableName WHERE source_type = ? AND source_key = ? AND source_field = ?",
                { rs, _ -> rs.getLong("id") },
                sourceType,
                sourceKey,
                sourceField,
            ).firstOrNull()

    private fun upsertLocale(
        localeTableName: String,
        sourceType: String,
        sourceKey: String,
        sourceField: String,
        locale: LocaleDTO,
    ): Long {
        val existingId = findLocaleId(localeTableName, sourceType, sourceKey, sourceField)
        if (existingId != null) {
            updateLocale(localeTableName, existingId, sourceType, sourceKey, sourceField, locale)
            return existingId
        }
        return insertLocale(localeTableName, sourceType, sourceKey, sourceField, locale)
    }

    private fun insertLocale(
        localeTableName: String,
        sourceType: String,
        sourceKey: String,
        sourceField: String,
        locale: LocaleDTO,
    ): Long =
        SimpleJdbcInsert(jdbcTemplate)
            .withTableName(localeTableName)
            .usingGeneratedKeyColumns("id")
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
        localeTableName: String,
        id: Long,
        sourceType: String,
        sourceKey: String,
        sourceField: String,
        locale: LocaleDTO,
    ) {
        jdbcTemplate.update(
            """
            UPDATE $localeTableName
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

    private fun deleteLocaleIds(
        localeIds: Set<Long>,
        localeTableName: String,
    ) {
        if (localeIds.isEmpty()) return
        localeIds.chunked(MODIFIED_CRAFTING_METADATA_JDBC_CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            jdbcTemplate.update("DELETE FROM $localeTableName WHERE id IN ($placeholders)", *chunk.toTypedArray())
        }
    }

    private fun resolveReferencedLocaleTable(
        tableName: String,
        columnName: String,
    ): String =
        jdbcTemplate
            .query(
                """
                SELECT referenced_table_name
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                  AND referenced_table_name IS NOT NULL
                """.trimIndent(),
                { rs, _ -> rs.getString("referenced_table_name") },
                tableName,
                columnName,
            ).firstOrNull() ?: "locale"

    private fun SkillTier.resolveRecipeDetails(
        recipesById: Map<Int, Recipe>,
        slotsById: Map<Int, ModifiedCraftingSlot>,
    ): SkillTier =
        copy(
            categories =
                categories.map { category ->
                    category.copy(
                        recipes =
                            category.recipes.mapNotNull { recipe ->
                                recipesById[recipe.id]?.enrichSlots(slotsById)
                            },
                    )
                },
        )

    private fun Recipe.enrichSlots(slotsById: Map<Int, ModifiedCraftingSlot>): Recipe =
        copy(
            modifiedCraftingSlots =
                modifiedCraftingSlots.map { slot ->
                    val metadata = slotsById[slot.id] ?: return@map slot
                    metadata.copy(displayOrder = slot.displayOrder)
                },
        )
}
