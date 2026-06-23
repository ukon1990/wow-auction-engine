package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.generated.model.AdminExpansion1
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRange
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.generated.model.AdminExpansionRequest
import net.jonasmf.auctionengine.mapper.toGameLocale
import net.jonasmf.auctionengine.mapper.toLocaleDTO
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class ExpansionRangeApplySummary(
    val matchedItemCount: Long,
    val updatedItemCount: Int,
    val conflictItemCount: Long,
)

@Repository
class AdminExpansionRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val localeJdbcRepository: LocaleJdbcRepository,
) {
    fun listExpansions(localeColumnSuffix: String = DEFAULT_LOCALE_COLUMN_SUFFIX): List<AdminExpansion1> =
        jdbcTemplate.query(
            expansionSelectSql("ORDER BY e.display_order, e.id", localeColumnSuffix),
        ) { rs, _ -> rs.toAdminExpansion(localeColumnSuffix) }

    fun findExpansion(
        id: Int,
        localeColumnSuffix: String = DEFAULT_LOCALE_COLUMN_SUFFIX,
    ): AdminExpansion1? =
        jdbcTemplate
            .query(
                expansionSelectSql("WHERE e.id = ?", localeColumnSuffix),
                { rs, _ -> rs.toAdminExpansion(localeColumnSuffix) },
                id,
            ).firstOrNull()

    fun expansionExists(expansionId: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expansion WHERE id = ?",
            Long::class.java,
            expansionId,
        )!! > 0

    fun slugExists(
        slug: String,
        idToIgnore: Int? = null,
    ): Boolean {
        val params = mutableListOf<Any>(slug)
        val ignoreSql =
            if (idToIgnore == null) {
                ""
            } else {
                params += idToIgnore
                "AND id <> ?"
            }
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expansion WHERE slug = ? $ignoreSql",
            Long::class.java,
            *params.toTypedArray(),
        )!! > 0
    }

    fun majorVersionExists(
        majorVersion: Int,
        idToIgnore: Int? = null,
    ): Boolean {
        val params = mutableListOf<Any>(majorVersion)
        val ignoreSql =
            if (idToIgnore == null) {
                ""
            } else {
                params += idToIgnore
                "AND id <> ?"
            }
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expansion WHERE major_version = ? $ignoreSql",
            Long::class.java,
            *params.toTypedArray(),
        )!! > 0
    }

    fun createExpansion(request: AdminExpansionRequest): AdminExpansion1 {
        val nameId =
            localeJdbcRepository.upsert(
                LocaleSourceType.EXPANSION,
                request.id.toString(),
                "name",
                request.nameLocales.toLocaleDTO(),
            )
        jdbcTemplate.update(
            """
            INSERT INTO expansion (id, slug, name_id, major_version, display_order)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            request.id,
            request.slug,
            nameId,
            request.majorVersion,
            request.displayOrder,
        )
        return findExpansion(request.id) ?: error("Created expansion ${request.id} was not found")
    }

    fun updateExpansion(
        id: Int,
        request: AdminExpansionRequest,
    ): AdminExpansion1? {
        val existing =
            jdbcTemplate
                .query(
                    "SELECT name_id FROM expansion WHERE id = ?",
                    { rs, _ -> rs.getLong("name_id") },
                    id,
                ).firstOrNull() ?: return null

        localeJdbcRepository.upsert(
            LocaleSourceType.EXPANSION,
            id.toString(),
            "name",
            request.nameLocales.toLocaleDTO(),
        )
        jdbcTemplate.update(
            """
            UPDATE expansion
            SET slug = ?,
                major_version = ?,
                display_order = ?
            WHERE id = ?
            """.trimIndent(),
            request.slug,
            request.majorVersion,
            request.displayOrder,
            id,
        )
        return findExpansion(id)
    }

    fun isExpansionReferenced(id: Int): Boolean {
        val rangeCount =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expansion_item_range WHERE expansion_id = ?",
                Long::class.java,
                id,
            )!!
        if (rangeCount > 0) return true
        val itemCount =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `item` WHERE expansion_id = ?",
                Long::class.java,
                id,
            )!!
        return itemCount > 0
    }

    fun deleteExpansion(id: Int): Boolean {
        val nameId =
            jdbcTemplate
                .query(
                    "SELECT name_id FROM expansion WHERE id = ?",
                    { rs, _ -> rs.getLong("name_id") },
                    id,
                ).firstOrNull() ?: return false
        if (jdbcTemplate.update("DELETE FROM expansion WHERE id = ?", id) == 0) {
            return false
        }
        localeJdbcRepository.deleteById(nameId)
        return true
    }

    fun listRanges(localeColumnSuffix: String = DEFAULT_LOCALE_COLUMN_SUFFIX): List<AdminExpansionItemRange> =
        jdbcTemplate.query(
            rangeSelectSql("ORDER BY r.start_item_id, r.end_item_id, r.id", localeColumnSuffix),
        ) { rs, _ -> rs.toAdminExpansionItemRange(localeColumnSuffix) }

    fun findRange(
        id: Long,
        localeColumnSuffix: String = DEFAULT_LOCALE_COLUMN_SUFFIX,
    ): AdminExpansionItemRange? =
        jdbcTemplate
            .query(
                rangeSelectSql("WHERE r.id = ?", localeColumnSuffix),
                { rs, _ -> rs.toAdminExpansionItemRange(localeColumnSuffix) },
                id,
            ).firstOrNull()

    fun hasOverlappingEnabledRange(
        idToIgnore: Long?,
        request: AdminExpansionItemRangeRequest,
    ): Boolean {
        if (!request.enabled) return false
        val params = mutableListOf<Any?>(
            request.expansionId,
            request.endItemId,
            request.startItemId,
        )
        val ignoreSql =
            if (idToIgnore == null) {
                ""
            } else {
                params += idToIgnore
                "AND id <> ?"
            }
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM expansion_item_range
            WHERE enabled = TRUE
              AND expansion_id <> ?
              AND start_item_id <= ?
              AND end_item_id >= ?
              $ignoreSql
            """.trimIndent(),
            Long::class.java,
            *params.toTypedArray(),
        )!! > 0
    }

    fun createRange(request: AdminExpansionItemRangeRequest): AdminExpansionItemRange {
        jdbcTemplate.update(
            """
            INSERT INTO expansion_item_range (expansion_id, start_item_id, end_item_id, source, enabled, note)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            request.expansionId,
            request.startItemId,
            request.endItemId,
            request.source,
            request.enabled,
            request.note,
        )
        val id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        return findRange(id) ?: error("Created expansion range $id was not found")
    }

    fun updateRange(
        id: Long,
        request: AdminExpansionItemRangeRequest,
    ): AdminExpansionItemRange? {
        val updated =
            jdbcTemplate.update(
                """
                UPDATE expansion_item_range
                SET expansion_id = ?,
                    start_item_id = ?,
                    end_item_id = ?,
                    source = ?,
                    enabled = ?,
                    note = ?
                WHERE id = ?
                """.trimIndent(),
                request.expansionId,
                request.startItemId,
                request.endItemId,
                request.source,
                request.enabled,
                request.note,
                id,
            )
        if (updated == 0) return null
        return findRange(id)
    }

    fun deleteRange(id: Long): Boolean = jdbcTemplate.update("DELETE FROM expansion_item_range WHERE id = ?", id) > 0

    fun applyEnabledRanges(): ExpansionRangeApplySummary {
        val conflictItemCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT i.id
                    FROM `item` i
                        INNER JOIN expansion_item_range r
                            ON r.enabled = TRUE
                            AND i.id BETWEEN r.start_item_id AND r.end_item_id
                    GROUP BY i.id
                    HAVING COUNT(DISTINCT r.expansion_id) > 1
                ) conflicts
                """.trimIndent(),
                Long::class.java,
            )!!
        val matchedItemCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT i.id
                    FROM `item` i
                        INNER JOIN expansion_item_range r
                            ON r.enabled = TRUE
                            AND i.id BETWEEN r.start_item_id AND r.end_item_id
                    GROUP BY i.id
                    HAVING COUNT(DISTINCT r.expansion_id) = 1
                ) matched
                """.trimIndent(),
                Long::class.java,
            )!!
        val updatedItemCount =
            jdbcTemplate.update(
                """
                UPDATE `item` i
                    INNER JOIN (
                        SELECT i2.id AS item_id, MIN(r.expansion_id) AS expansion_id
                        FROM `item` i2
                            INNER JOIN expansion_item_range r
                                ON r.enabled = TRUE
                                AND i2.id BETWEEN r.start_item_id AND r.end_item_id
                        GROUP BY i2.id
                        HAVING COUNT(DISTINCT r.expansion_id) = 1
                    ) matched ON matched.item_id = i.id
                SET i.expansion_id = matched.expansion_id
                """.trimIndent(),
            )
        return ExpansionRangeApplySummary(
            matchedItemCount = matchedItemCount,
            updatedItemCount = updatedItemCount,
            conflictItemCount = conflictItemCount,
        )
    }

    private fun expansionSelectSql(
        suffix: String,
        localeColumnSuffix: String,
    ): String =
        """
        SELECT
            e.id,
            e.slug,
            e.major_version,
            e.display_order,
            COALESCE(l.$localeColumnSuffix, l.en_gb, l.en_us, e.slug) AS expansion_name,
            l.en_us,
            l.en_gb,
            l.de_de,
            l.es_es,
            l.es_mx,
            l.fr_fr,
            l.it_it,
            l.ko_kr,
            l.pt_br,
            l.pt_pt,
            l.ru_ru,
            l.zh_cn,
            l.zh_tw
        FROM expansion e
            INNER JOIN locale l ON l.id = e.name_id
        $suffix
        """.trimIndent()

    private fun rangeSelectSql(
        suffix: String,
        localeColumnSuffix: String,
    ): String =
        """
        SELECT
            r.id,
            r.start_item_id,
            r.end_item_id,
            r.source,
            r.enabled,
            r.note,
            r.created_at,
            r.updated_at,
            e.id AS expansion_id,
            e.slug AS expansion_slug,
            e.major_version AS expansion_major_version,
            e.display_order AS expansion_display_order,
            COALESCE(l.$localeColumnSuffix, l.en_gb, l.en_us, e.slug) AS expansion_name,
            l.en_us,
            l.en_gb,
            l.de_de,
            l.es_es,
            l.es_mx,
            l.fr_fr,
            l.it_it,
            l.ko_kr,
            l.pt_br,
            l.pt_pt,
            l.ru_ru,
            l.zh_cn,
            l.zh_tw
        FROM expansion_item_range r
            INNER JOIN expansion e ON e.id = r.expansion_id
            INNER JOIN locale l ON l.id = e.name_id
        $suffix
        """.trimIndent()

    companion object {
        const val DEFAULT_LOCALE_COLUMN_SUFFIX = "en_gb"

        fun resolveLocaleColumnSuffix(locale: String?): String =
            locale
                ?.let { localeValue ->
                    runCatching { Locale.getAllValues().getValue(localeValue) }
                        .recoverCatching { Locale.fromCompactString(localeValue) }
                        .getOrNull()
                        ?.value
                        ?.lowercase()
                } ?: DEFAULT_LOCALE_COLUMN_SUFFIX
    }
}

private fun ResultSet.toAdminExpansion(localeColumnSuffix: String): AdminExpansion1 {
    val locale = toLocaleDTO()
    return AdminExpansion1(
        id = getInt("id"),
        slug = getString("slug"),
        name = getString("expansion_name"),
        nameLocales = locale.toGameLocale(),
        majorVersion = getInt("major_version"),
        displayOrder = getInt("display_order"),
    )
}

private fun ResultSet.toAdminExpansionItemRange(localeColumnSuffix: String): AdminExpansionItemRange =
    AdminExpansionItemRange(
        id = getLong("id"),
        expansion = toNestedAdminExpansion(),
        startItemId = getInt("start_item_id"),
        endItemId = getInt("end_item_id"),
        source = getString("source"),
        enabled = getBoolean("enabled"),
        note = getString("note"),
        createdAt = getTimestamp("created_at").toOffsetDateTime(),
        updatedAt = getTimestamp("updated_at").toOffsetDateTime(),
    )

private fun ResultSet.toNestedAdminExpansion(): AdminExpansion1 {
    val locale = toLocaleDTO()
    return AdminExpansion1(
        id = getInt("expansion_id"),
        slug = getString("expansion_slug"),
        name = getString("expansion_name"),
        nameLocales = locale.toGameLocale(),
        majorVersion = getInt("expansion_major_version"),
        displayOrder = getInt("expansion_display_order"),
    )
}

private fun Timestamp.toOffsetDateTime(): OffsetDateTime = toInstant().atOffset(ZoneOffset.UTC)
