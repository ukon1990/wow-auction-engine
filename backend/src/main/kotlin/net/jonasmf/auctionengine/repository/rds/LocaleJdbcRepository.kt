package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dto.LocaleDTO
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.simple.SimpleJdbcInsert
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class LocaleJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val localeInsert = SimpleJdbcInsert(jdbcTemplate).withTableName("locale").usingGeneratedKeyColumns("id")

    fun findLocaleId(
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

    fun findById(id: Long): LocaleDTO? =
        jdbcTemplate
            .query(
                """
                SELECT en_us, en_gb, de_de, es_es, es_mx, fr_fr, it_it, ko_kr, pt_br, pt_pt, ru_ru, zh_cn, zh_tw
                FROM locale
                WHERE id = ?
                """.trimIndent(),
                { rs, _ -> rs.toLocaleDTO() },
                id,
            ).firstOrNull()

    fun upsert(
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

    fun deleteById(id: Long) {
        jdbcTemplate.update("DELETE FROM locale WHERE id = ?", id)
    }

    private fun insertLocale(
        sourceType: String,
        sourceKey: String,
        sourceField: String,
        locale: LocaleDTO,
    ): Long =
        localeInsert
            .executeAndReturnKey(localeParameters(sourceType, sourceKey, sourceField, locale))
            .toLong()

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

    private fun localeParameters(
        sourceType: String,
        sourceKey: String,
        sourceField: String,
        locale: LocaleDTO,
    ): MapSqlParameterSource =
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
            .addValue("zh_cn", locale.zh_CN)
}

fun ResultSet.toLocaleDTO(): LocaleDTO =
    LocaleDTO(
        en_US = getString("en_us"),
        en_GB = getString("en_gb"),
        de_DE = getString("de_de"),
        es_ES = getString("es_es"),
        es_MX = getString("es_mx"),
        fr_FR = getString("fr_fr"),
        it_IT = getString("it_it"),
        ko_KR = getString("ko_kr"),
        pt_BR = getString("pt_br"),
        pt_PT = getString("pt_pt"),
        ru_RU = getString("ru_ru"),
        zh_CN = getString("zh_cn"),
        zh_TW = getString("zh_tw"),
    )
