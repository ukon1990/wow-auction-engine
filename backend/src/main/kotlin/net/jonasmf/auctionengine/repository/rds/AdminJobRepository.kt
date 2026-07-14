package net.jonasmf.auctionengine.repository.rds

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.jonasmf.auctionengine.generated.model.AdminJob
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class AdminJobRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val objectMapper = jacksonObjectMapper()

    fun createJob(
        domain: String,
        operation: String,
        requestedBy: String?,
    ): AdminJob {
        jdbcTemplate.update(
            """
            INSERT INTO admin_job (domain, operation, status, requested_by)
            VALUES (?, ?, 'running', ?)
            """.trimIndent(),
            domain,
            operation,
            requestedBy,
        )
        val id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        return findJob(id) ?: error("Created admin job $id was not found")
    }

    fun completeJob(
        id: Long,
        summary: Map<String, Any?>,
    ) {
        jdbcTemplate.update(
            """
            UPDATE admin_job
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
    ) = failJob(id, error.message?.take(1024) ?: error.javaClass.simpleName)

    fun failJob(
        id: Long,
        errorMessage: String,
    ) {
        jdbcTemplate.update(
            """
            UPDATE admin_job
            SET status = 'failed',
                finished_at = CURRENT_TIMESTAMP,
                error_message = ?
            WHERE id = ?
            """.trimIndent(),
            errorMessage.take(1024),
            id,
        )
    }

    fun updateJobProgress(
        id: Long,
        progress: Map<String, Any?>,
    ) {
        jdbcTemplate.update(
            """
            UPDATE admin_job
            SET summary_json = ?
            WHERE id = ? AND status = 'running'
            """.trimIndent(),
            objectMapper.writeValueAsString(progress),
            id,
        )
    }

    fun findJob(id: Long): AdminJob? =
        jdbcTemplate
            .query(
                """
                SELECT id, domain, operation, status, requested_by, started_at, finished_at, summary_json, error_message
                FROM admin_job
                WHERE id = ?
                """.trimIndent(),
                { rs, _ -> rs.toAdminJob(objectMapper) },
                id,
            ).firstOrNull()

    fun findRunningJob(
        domain: String,
        operation: String,
    ): AdminJob? =
        jdbcTemplate
            .query(
                """
                SELECT id, domain, operation, status, requested_by, started_at, finished_at, summary_json, error_message
                FROM admin_job
                WHERE domain = ? AND operation = ? AND status = 'running'
                ORDER BY started_at DESC, id DESC
                LIMIT 1
                """.trimIndent(),
                { rs, _ -> rs.toAdminJob(objectMapper) },
                domain,
                operation,
            ).firstOrNull()
}

private fun ResultSet.toAdminJob(objectMapper: ObjectMapper): AdminJob {
    val summaryJson = getString("summary_json")
    val summary =
        summaryJson?.let {
            objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {})
        }
    return AdminJob(
        id = getLong("id"),
        domain = AdminJob.Domain.forValue(getString("domain")),
        operation = getString("operation"),
        status = AdminJob.Status.forValue(getString("status")),
        requestedBy = getString("requested_by"),
        startedAt = getTimestamp("started_at").toOffsetDateTime(),
        finishedAt = getTimestamp("finished_at")?.toOffsetDateTime(),
        summary = summary,
        errorMessage = getString("error_message"),
    )
}

private fun Timestamp.toOffsetDateTime(): OffsetDateTime = toInstant().atOffset(ZoneOffset.UTC)
