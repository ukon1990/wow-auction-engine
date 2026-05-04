package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val MEDIA_FETCH_FAILURE_JDBC_CHUNK_SIZE = 200

enum class BlizzardMediaFetchEntityKind(
    val tableName: String,
) {
    ITEM_APPEARANCE("item_appearance"),
    RECIPE("recipe"),
    PROFESSION("profession"),
    ;

    companion object {
        fun forTable(tableName: String): BlizzardMediaFetchEntityKind? =
            entries.firstOrNull { it.tableName == tableName }
    }
}

data class MediaFetchFailureState(
    val entityKind: BlizzardMediaFetchEntityKind,
    val entityId: Int,
    val failureCount: Int,
    val lastErrorStatus: String?,
    val lastErrorMessage: String?,
    val lastFailedAt: OffsetDateTime,
    val nextRetryAt: OffsetDateTime?,
    val manualDisabled: Boolean,
)

@Repository
class BlizzardMediaFetchFailureRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findFailureStates(
        entityKind: BlizzardMediaFetchEntityKind,
        entityIds: Collection<Int>,
    ): Map<Int, MediaFetchFailureState> {
        if (entityIds.isEmpty()) return emptyMap()
        val kind = entityKind.name
        val states = linkedMapOf<Int, MediaFetchFailureState>()
        entityIds
            .distinct()
            .chunked(MEDIA_FETCH_FAILURE_JDBC_CHUNK_SIZE)
            .forEach { chunk ->
                jdbcTemplate.query(
                    """
                    SELECT
                        entity_id,
                        failure_count,
                        last_error_status,
                        last_error_message,
                        last_failed_at,
                        next_retry_at,
                        manual_disabled
                    FROM blizzard_media_fetch_failure
                    WHERE entity_kind = ?
                      AND entity_id IN (${placeholders(chunk.size)})
                    """.trimIndent(),
                    { rs, _ ->
                        MediaFetchFailureState(
                            entityKind = entityKind,
                            entityId = rs.getInt("entity_id"),
                            failureCount = rs.getInt("failure_count"),
                            lastErrorStatus = rs.getString("last_error_status"),
                            lastErrorMessage = rs.getString("last_error_message"),
                            lastFailedAt = rs.getObject("last_failed_at", OffsetDateTime::class.java),
                            nextRetryAt = rs.getObject("next_retry_at", OffsetDateTime::class.java),
                            manualDisabled = rs.getBoolean("manual_disabled"),
                        )
                    },
                    kind,
                    *chunk.toTypedArray(),
                ).forEach { state -> states[state.entityId] = state }
            }
        return states
    }

    fun classifyRetryEligibility(
        entityKind: BlizzardMediaFetchEntityKind,
        entityIds: Collection<Int>,
        now: OffsetDateTime,
    ): ItemRetryEligibility {
        if (entityIds.isEmpty()) {
            return ItemRetryEligibility(
                retryableIds = emptyList(),
                cooldownSkippedIds = emptyList(),
                manualDisabledIds = emptyList(),
            )
        }
        val states = findFailureStates(entityKind, entityIds)
        val retryable = mutableListOf<Int>()
        val cooldownSkipped = mutableListOf<Int>()
        val manualDisabled = mutableListOf<Int>()

        entityIds.distinct().forEach { entityId ->
            val state = states[entityId]
            when {
                state == null -> retryable += entityId
                state.manualDisabled -> manualDisabled += entityId
                state.nextRetryAt != null && state.nextRetryAt.isAfter(now) -> cooldownSkipped += entityId
                else -> retryable += entityId
            }
        }

        return ItemRetryEligibility(
            retryableIds = retryable,
            cooldownSkippedIds = cooldownSkipped,
            manualDisabledIds = manualDisabled,
        )
    }

    fun upsertFailureState(
        entityKind: BlizzardMediaFetchEntityKind,
        entityId: Int,
        failureCount: Int,
        lastErrorStatus: String?,
        lastErrorMessage: String?,
        lastFailedAt: OffsetDateTime,
        nextRetryAt: OffsetDateTime?,
        manualDisabled: Boolean,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO blizzard_media_fetch_failure (
                entity_kind,
                entity_id,
                failure_count,
                last_error_status,
                last_error_message,
                last_failed_at,
                next_retry_at,
                manual_disabled
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                failure_count = VALUES(failure_count),
                last_error_status = VALUES(last_error_status),
                last_error_message = VALUES(last_error_message),
                last_failed_at = VALUES(last_failed_at),
                next_retry_at = VALUES(next_retry_at),
                manual_disabled = VALUES(manual_disabled)
            """.trimIndent(),
            entityKind.name,
            entityId,
            failureCount,
            lastErrorStatus,
            lastErrorMessage,
            lastFailedAt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
            nextRetryAt?.withOffsetSameInstant(ZoneOffset.UTC)?.toLocalDateTime(),
            manualDisabled,
        )
    }

    fun clearFailureStates(
        entityKind: BlizzardMediaFetchEntityKind,
        entityIds: Collection<Int>,
    ) {
        if (entityIds.isEmpty()) return
        val kind = entityKind.name
        entityIds
            .distinct()
            .chunked(MEDIA_FETCH_FAILURE_JDBC_CHUNK_SIZE)
            .forEach { chunk ->
                jdbcTemplate.update(
                    """
                    DELETE FROM blizzard_media_fetch_failure
                    WHERE entity_kind = ?
                      AND entity_id IN (${placeholders(chunk.size)})
                    """.trimIndent(),
                    kind,
                    *chunk.toTypedArray(),
                )
            }
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")
}
