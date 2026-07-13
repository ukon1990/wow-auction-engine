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
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
