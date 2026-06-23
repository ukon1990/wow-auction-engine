package net.jonasmf.auctionengine.repository.rds

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.jonasmf.auctionengine.generated.model.AdminExpansion
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRange
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.generated.model.AdminItemJob
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
) {
    private val objectMapper = jacksonObjectMapper()
    fun listExpansions(): List<AdminExpansion> =
        jdbcTemplate.query(
            """
            SELECT id, slug, name, major_version, display_order
            FROM expansion
            ORDER BY display_order, id
            """.trimIndent(),
        ) { rs, _ -> rs.toAdminExpansion() }

    fun expansionExists(expansionId: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expansion WHERE id = ?",
            Long::class.java,
            expansionId,
        )!! > 0

    fun listRanges(): List<AdminExpansionItemRange> =
        jdbcTemplate.query(
            rangeSelectSql("ORDER BY r.start_item_id, r.end_item_id, r.id"),
        ) { rs, _ -> rs.toAdminExpansionItemRange() }

    fun findRange(id: Long): AdminExpansionItemRange? =
        jdbcTemplate
            .query(
                rangeSelectSql("WHERE r.id = ?"),
                { rs, _ -> rs.toAdminExpansionItemRange() },
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

    fun createJob(
        type: String,
        requestedBy: String?,
    ): AdminItemJob {
        jdbcTemplate.update(
            """
            INSERT INTO admin_item_job (type, status, requested_by)
            VALUES (?, 'running', ?)
            """.trimIndent(),
            type,
            requestedBy,
        )
        val id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        return findJob(id) ?: error("Created admin item job $id was not found")
    }

    fun completeJob(
        id: Long,
        summary: Map<String, Any?>,
    ) {
        jdbcTemplate.update(
            """
            UPDATE admin_item_job
            SET status = 'completed',
                finished_at = CURRENT_TIMESTAMP,
                summary_json = ?
            WHERE id = ?
            """.trimIndent(),
            objectMapper.writeValueAsString(summary),
            id,
        )
    }

    fun failJob(
        id: Long,
        error: Throwable,
    ) {
        jdbcTemplate.update(
            """
            UPDATE admin_item_job
            SET status = 'failed',
                finished_at = CURRENT_TIMESTAMP,
                error_message = ?
            WHERE id = ?
            """.trimIndent(),
            error.message?.take(1024) ?: error.javaClass.simpleName,
            id,
        )
    }

    fun findJob(id: Long): AdminItemJob? =
        jdbcTemplate
            .query(
                """
                SELECT id, type, status, requested_by, started_at, finished_at, summary_json, error_message
                FROM admin_item_job
                WHERE id = ?
                """.trimIndent(),
                { rs, _ -> rs.toAdminItemJob(objectMapper) },
                id,
            ).firstOrNull()

    private fun rangeSelectSql(suffix: String): String =
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
            e.name AS expansion_name,
            e.major_version AS expansion_major_version,
            e.display_order AS expansion_display_order
        FROM expansion_item_range r
            INNER JOIN expansion e ON e.id = r.expansion_id
        $suffix
        """.trimIndent()
}

private fun ResultSet.toAdminExpansion(): AdminExpansion =
    AdminExpansion(
        id = getInt("id"),
        slug = getString("slug"),
        name = getString("name"),
        majorVersion = getInt("major_version"),
        displayOrder = getInt("display_order"),
    )

private fun ResultSet.toAdminExpansionItemRange(): AdminExpansionItemRange =
    AdminExpansionItemRange(
        id = getLong("id"),
        expansion =
            AdminExpansion(
                id = getInt("expansion_id"),
                slug = getString("expansion_slug"),
                name = getString("expansion_name"),
                majorVersion = getInt("expansion_major_version"),
                displayOrder = getInt("expansion_display_order"),
            ),
        startItemId = getInt("start_item_id"),
        endItemId = getInt("end_item_id"),
        source = getString("source"),
        enabled = getBoolean("enabled"),
        note = getString("note"),
        createdAt = getTimestamp("created_at").toOffsetDateTime(),
        updatedAt = getTimestamp("updated_at").toOffsetDateTime(),
    )

private fun ResultSet.toAdminItemJob(objectMapper: ObjectMapper): AdminItemJob {
    val summaryJson = getString("summary_json")
    val summary =
        summaryJson?.let {
            objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {})
        }
    return AdminItemJob(
        id = getLong("id"),
        type = getString("type"),
        status = AdminItemJob.Status.forValue(getString("status")),
        requestedBy = getString("requested_by"),
        startedAt = getTimestamp("started_at").toOffsetDateTime(),
        finishedAt = getTimestamp("finished_at")?.toOffsetDateTime(),
        summary = summary,
        errorMessage = getString("error_message"),
    )
}

private fun Timestamp.toOffsetDateTime(): OffsetDateTime = toInstant().atOffset(ZoneOffset.UTC)
