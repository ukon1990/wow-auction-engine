package net.jonasmf.auctionengine.repository.rds

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionData
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.security.MessageDigest

@Repository
class NormalizedProfessionImportRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val objectMapper = jacksonObjectMapper()

    fun save(
        payload: NormalizedAuctionHelperProfessionData,
        professionCount: Int,
        recipeCount: Int,
        ownerSubject: String = "admin",
    ) {
        val payloadJson = objectMapper.writeValueAsString(payload)
        val sourceFilesJson = objectMapper.writeValueAsString(payload.source.files)
        val contentHash = payloadJson.sha256()
        jdbcTemplate.update(
            """
            INSERT INTO normalized_profession_import (
                content_hash, contract_version, addon, addon_version, processor_version,
                source_files, character_count, profession_count, recipe_count, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                imported_at = CURRENT_TIMESTAMP,
                addon_version = VALUES(addon_version),
                processor_version = VALUES(processor_version),
                source_files = VALUES(source_files),
                character_count = VALUES(character_count),
                profession_count = VALUES(profession_count),
                recipe_count = VALUES(recipe_count),
                payload = VALUES(payload)
            """.trimIndent(),
            contentHash,
            payload.contractVersion.value,
            payload.source.addon.value,
            payload.source.addonVersion,
            payload.source.processorVersion,
            sourceFilesJson,
            payload.characters.size,
            professionCount,
            recipeCount,
            payloadJson,
        )
        val importId = jdbcTemplate.queryForObject("SELECT id FROM normalized_profession_import WHERE content_hash = ?", Long::class.java, contentHash)!!
        payload.characters.forEach { character ->
            jdbcTemplate.update(
                """INSERT INTO user_character (owner_subject, region, realm_name, character_name, source_guid)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE region = VALUES(region), realm_name = VALUES(realm_name),
                        character_name = VALUES(character_name), source_guid = VALUES(source_guid),
                        updated_at = CURRENT_TIMESTAMP""".trimIndent(),
                ownerSubject,
                character.region.lowercase(),
                character.realm,
                character.name,
                character.characterKey,
            )
            val characterId =
                jdbcTemplate.queryForObject(
                    """SELECT id FROM user_character
                        WHERE owner_subject = ? AND region = ? AND realm_name = ? AND character_name = ?""".trimIndent(),
                    Long::class.java,
                    ownerSubject,
                    character.region.lowercase(),
                    character.realm,
                    character.name,
                )!!
            character.professions.forEach professionLoop@{ profession ->
                if (!exists("profession", profession.professionId)) return@professionLoop
                jdbcTemplate.update(
                    """INSERT INTO user_character_profession_profile (character_id, profession_id, skill_level)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE skill_level = VALUES(skill_level), updated_at = CURRENT_TIMESTAMP""".trimIndent(),
                    characterId,
                    profession.professionId,
                    profession.skillLevel,
                )
                val profileId =
                    jdbcTemplate.queryForObject(
                        "SELECT id FROM user_character_profession_profile WHERE character_id = ? AND profession_id = ?",
                        Long::class.java,
                        characterId,
                        profession.professionId,
                    )!!
                profession.recipes.forEach { recipe ->
                    jdbcTemplate.update(
                        """INSERT INTO user_character_profession_recipe
                            (profile_id, recipe_id, recipe_name, learned, source_import_id)
                            VALUES (?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE recipe_name = VALUES(recipe_name), learned = VALUES(learned),
                                source_import_id = VALUES(source_import_id), updated_at = CURRENT_TIMESTAMP""".trimIndent(),
                        profileId,
                        recipe.recipeId,
                        recipe.name,
                        recipe.learned,
                        importId,
                    )
                }
            }
        }
    }

    private fun exists(table: String, id: Int): Boolean =
        jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM $table WHERE id = ?)", Boolean::class.java, id) == true

    fun missingProfessionIds(professionIds: Set<Int>): Set<Int> = professionIds.filterNot { exists("profession", it) }.toSet()
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
