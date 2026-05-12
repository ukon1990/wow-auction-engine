package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.repository.rds.BlizzardMediaFetchEntityKind
import net.jonasmf.auctionengine.repository.rds.BlizzardMediaFetchFailureRepository
import net.jonasmf.auctionengine.repository.rds.ItemFetchFailureState
import net.jonasmf.auctionengine.repository.rds.ItemJdbcRepository
import net.jonasmf.auctionengine.repository.rds.MediaFetchFailureState
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime

private const val MEDIA_BACKFILL_CHUNK_SIZE = 500
private const val MEDIA_BACKFILL_DISABLE_FAILURE_COUNT = 10
private val MEDIA_BACKFILL_BASE_BACKOFF: Duration = Duration.ofHours(1)
private val MEDIA_BACKFILL_MAX_BACKOFF: Duration = Duration.ofDays(7)

private const val NEEDS_MEDIA_BACKFILL_SQL =
    "(media_url IS NULL OR media_url = '' OR media_url LIKE '%/data/wow/media/%')"

private data class MediaBackfillRow(
    val id: Int,
    val mediaLookupId: Int,
    val mediaUrl: String?,
    val mediaSourceUrl: String?,
)

data class BlizzardMediaBackfillResult(
    val region: Region,
    val itemUpdates: Int,
    val itemAppearanceUpdates: Int,
    val recipeUpdates: Int,
    val professionUpdates: Int,
) {
    val totalUpdates: Int = itemUpdates + itemAppearanceUpdates + recipeUpdates + professionUpdates
}

@Service
class BlizzardMediaBackfillService(
    private val properties: BlizzardApiProperties,
    private val jdbcTemplate: JdbcTemplate,
    private val blizzardMediaService: BlizzardMediaService,
    private val itemJdbcRepository: ItemJdbcRepository,
    private val blizzardMediaFetchFailureRepository: BlizzardMediaFetchFailureRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    @Value("\${app.media-backfill.concurrency:3}")
    private val mediaBackfillConcurrency: Int,
    @Value("\${app.media-backfill.start-delay-ms:50}")
    private val mediaBackfillStartDelayMs: Long,
) {
    private val log = LoggerFactory.getLogger(BlizzardMediaBackfillService::class.java)
    private val effectiveConcurrency: Int = mediaBackfillConcurrency.coerceIn(1, 32)

    fun backfillConfiguredStaticDataRegion(): BlizzardMediaBackfillResult = backfillRegion(properties.staticDataRegion)

    fun backfillRegion(region: Region): BlizzardMediaBackfillResult =
        BlizzardMediaBackfillResult(
            region = region,
            itemUpdates = backfillTable(region, "item", BlizzardMediaType.ITEM),
            itemAppearanceUpdates = backfillTable(region, "item_appearance", BlizzardMediaType.ITEM_APPEARANCE),
            recipeUpdates = backfillTable(region, "recipe", BlizzardMediaType.RECIPE),
            professionUpdates = backfillTable(region, "profession", BlizzardMediaType.PROFESSION),
        ).also { result ->
            log.info(
                "Finished Blizzard media backfill region={} itemUpdates={} itemAppearanceUpdates={} recipeUpdates={} professionUpdates={} totalUpdates={}",
                result.region,
                result.itemUpdates,
                result.itemAppearanceUpdates,
                result.recipeUpdates,
                result.professionUpdates,
                result.totalUpdates,
            )
        }

    private fun backfillTable(
        region: Region,
        tableName: String,
        type: BlizzardMediaType,
    ): Int {
        val mediaFetchKind = BlizzardMediaFetchEntityKind.forTable(tableName)
        var afterId = 0
        var updates = 0
        while (true) {
            val rows = readCandidateRows(tableName, afterId)
            if (rows.isEmpty()) break
            val now = OffsetDateTime.now(clock)
            val (retryableRows, eligibilityLog) =
                if (tableName == "item") {
                    val eligibility =
                        itemJdbcRepository.classifyItemRetryEligibility(
                            rows.map(MediaBackfillRow::id),
                            now,
                        )
                    val retryableIds = eligibility.retryableIds.toSet()
                    rows.filter { retryableIds.contains(it.id) } to
                        ChunkEligibility(
                            retryable = retryableIds.size,
                            cooldown = eligibility.cooldownSkippedIds.size,
                            manualDisabled = eligibility.manualDisabledIds.size,
                        )
                } else {
                    val kind =
                        requireNotNull(mediaFetchKind) {
                            "Unsupported media backfill table $tableName"
                        }
                    val eligibility =
                        blizzardMediaFetchFailureRepository.classifyRetryEligibility(
                            kind,
                            rows.map(MediaBackfillRow::id),
                            now,
                        )
                    val retryableIds = eligibility.retryableIds.toSet()
                    rows.filter { retryableIds.contains(it.id) } to
                        ChunkEligibility(
                            retryable = retryableIds.size,
                            cooldown = eligibility.cooldownSkippedIds.size,
                            manualDisabled = eligibility.manualDisabledIds.size,
                        )
                }

            log.info(
                "Media backfill eligibility table={} afterId={} chunkSize={} retryable={} cooldownSkipped={} manualDisabledSkipped={}",
                tableName,
                afterId,
                rows.size,
                eligibilityLog.retryable,
                eligibilityLog.cooldown,
                eligibilityLog.manualDisabled,
            )

            val existingItemFailureStates =
                if (tableName == "item") {
                    itemJdbcRepository
                        .findItemFetchFailureStates(retryableRows.map(MediaBackfillRow::id))
                        .toMutableMap()
                } else {
                    mutableMapOf()
                }
            val existingMediaFetchFailures =
                if (mediaFetchKind != null) {
                    blizzardMediaFetchFailureRepository
                        .findFailureStates(mediaFetchKind, retryableRows.map(MediaBackfillRow::id))
                        .toMutableMap()
                } else {
                    mutableMapOf()
                }

            updates +=
                runChunk(
                    region = region,
                    tableName = tableName,
                    type = type,
                    retryableRows = retryableRows,
                    mediaFetchKind = mediaFetchKind,
                    existingItemFailureStates = existingItemFailureStates,
                    existingMediaFetchFailures = existingMediaFetchFailures,
                )

            afterId = rows.maxOf { it.id }
        }
        return updates
    }

    private data class ChunkEligibility(
        val retryable: Int,
        val cooldown: Int,
        val manualDisabled: Int,
    )

    private fun runChunk(
        region: Region,
        tableName: String,
        type: BlizzardMediaType,
        retryableRows: List<MediaBackfillRow>,
        mediaFetchKind: BlizzardMediaFetchEntityKind?,
        existingItemFailureStates: MutableMap<Int, ItemFetchFailureState>,
        existingMediaFetchFailures: MutableMap<Int, MediaFetchFailureState>,
    ): Int {
        if (retryableRows.isEmpty()) return 0
        val baseFlux = Flux.fromIterable(retryableRows)
        val paced =
            if (mediaBackfillStartDelayMs > 0) {
                baseFlux.delayElements(Duration.ofMillis(mediaBackfillStartDelayMs))
            } else {
                baseFlux
            }
        return paced
            .flatMap(
                { row ->
                    Mono
                        .fromCallable {
                            resolveAndUpdate(
                                region = region,
                                tableName = tableName,
                                type = type,
                                row = row,
                                mediaFetchKind = mediaFetchKind,
                                existingItemFailureStates = existingItemFailureStates,
                                existingMediaFetchFailures = existingMediaFetchFailures,
                            )
                        }.subscribeOn(Schedulers.boundedElastic())
                },
                effectiveConcurrency,
            ).collectList()
            .block()
            .orEmpty()
            .count { it }
    }

    private fun readCandidateRows(
        tableName: String,
        afterId: Int,
    ): List<MediaBackfillRow> {
        val mediaLookupExpression =
            if (tableName == "item_appearance") {
                "item_display_info_id"
            } else {
                "id"
            }
        return jdbcTemplate.query(
            """
            SELECT id, $mediaLookupExpression AS media_lookup_id, media_url, media_source_url
            FROM `$tableName`
            WHERE $NEEDS_MEDIA_BACKFILL_SQL
              AND id > ?
            ORDER BY id
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                MediaBackfillRow(
                    id = rs.getInt("id"),
                    mediaLookupId = rs.getInt("media_lookup_id"),
                    mediaUrl = rs.getString("media_url"),
                    mediaSourceUrl = rs.getString("media_source_url"),
                )
            },
            afterId,
            MEDIA_BACKFILL_CHUNK_SIZE,
        )
    }

    private fun resolveAndUpdate(
        region: Region,
        tableName: String,
        type: BlizzardMediaType,
        row: MediaBackfillRow,
        mediaFetchKind: BlizzardMediaFetchEntityKind?,
        existingItemFailureStates: MutableMap<Int, ItemFetchFailureState>,
        existingMediaFetchFailures: MutableMap<Int, MediaFetchFailureState>,
    ): Boolean {
        val sourceHref = sourceHref(row)
        val resolved = blizzardMediaService.resolve(region, type, row.mediaLookupId, sourceHref, row.id)
        if (resolved != null) {
            jdbcTemplate.update(
                """
                UPDATE `$tableName`
                SET media_url = ?, media_source_url = ?
                WHERE id = ?
                """.trimIndent(),
                resolved.mediaUrl,
                resolved.mediaSourceUrl,
                row.id,
            )
            if (tableName == "item") {
                itemJdbcRepository.clearItemFetchFailureStates(listOf(row.id))
            } else if (mediaFetchKind != null) {
                blizzardMediaFetchFailureRepository.clearFailureStates(mediaFetchKind, listOf(row.id))
            }
            return true
        }

        if (tableName == "item") {
            recordItemFailure(row.id, existingItemFailureStates)
        } else if (mediaFetchKind != null) {
            recordMediaFetchFailure(row.id, mediaFetchKind, existingMediaFetchFailures)
        }
        return false
    }

    private fun recordItemFailure(
        itemId: Int,
        existingFailureStates: MutableMap<Int, ItemFetchFailureState>,
    ) {
        val failedAt = OffsetDateTime.now(clock)
        val previousFailureCount = existingFailureStates[itemId]?.failureCount ?: 0
        val currentFailureCount = previousFailureCount + 1
        val manualDisabled = currentFailureCount >= MEDIA_BACKFILL_DISABLE_FAILURE_COUNT
        val nextRetryAt =
            if (manualDisabled) {
                null
            } else {
                failedAt.plus(backoffForFailureCount(currentFailureCount))
            }
        itemJdbcRepository.upsertItemFetchFailureState(
            itemId = itemId,
            failureCount = currentFailureCount,
            lastErrorStatus = null,
            lastErrorMessage = "media resolution returned null",
            lastFailedAt = failedAt,
            nextRetryAt = nextRetryAt,
            manualDisabled = manualDisabled,
        )
        existingFailureStates[itemId] =
            ItemFetchFailureState(
                itemId = itemId,
                failureCount = currentFailureCount,
                lastErrorStatus = null,
                lastErrorMessage = "media resolution returned null",
                lastFailedAt = failedAt,
                nextRetryAt = nextRetryAt,
                manualDisabled = manualDisabled,
            )
    }

    private fun recordMediaFetchFailure(
        entityId: Int,
        kind: BlizzardMediaFetchEntityKind,
        existing: MutableMap<Int, MediaFetchFailureState>,
    ) {
        val failedAt = OffsetDateTime.now(clock)
        val previousFailureCount = existing[entityId]?.failureCount ?: 0
        val currentFailureCount = previousFailureCount + 1
        val manualDisabled = currentFailureCount >= MEDIA_BACKFILL_DISABLE_FAILURE_COUNT
        val nextRetryAt =
            if (manualDisabled) {
                null
            } else {
                failedAt.plus(backoffForFailureCount(currentFailureCount))
            }
        blizzardMediaFetchFailureRepository.upsertFailureState(
            entityKind = kind,
            entityId = entityId,
            failureCount = currentFailureCount,
            lastErrorStatus = null,
            lastErrorMessage = "media resolution returned null",
            lastFailedAt = failedAt,
            nextRetryAt = nextRetryAt,
            manualDisabled = manualDisabled,
        )
        existing[entityId] =
            MediaFetchFailureState(
                entityKind = kind,
                entityId = entityId,
                failureCount = currentFailureCount,
                lastErrorStatus = null,
                lastErrorMessage = "media resolution returned null",
                lastFailedAt = failedAt,
                nextRetryAt = nextRetryAt,
                manualDisabled = manualDisabled,
            )
    }

    private fun backoffForFailureCount(failureCount: Int): Duration {
        val shift = (failureCount - 1).coerceAtLeast(0)
        val exponential = if (shift >= 62) Long.MAX_VALUE else 1L shl shift
        val backoff = MEDIA_BACKFILL_BASE_BACKOFF.multipliedBy(exponential)
        return if (backoff > MEDIA_BACKFILL_MAX_BACKOFF) MEDIA_BACKFILL_MAX_BACKOFF else backoff
    }

    private fun sourceHref(row: MediaBackfillRow): String? =
        row.mediaSourceUrl
            ?: row.mediaUrl?.takeIf { it.contains("/data/wow/media/") }
}
