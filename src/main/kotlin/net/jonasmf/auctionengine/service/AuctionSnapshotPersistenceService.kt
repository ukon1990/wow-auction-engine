package net.jonasmf.auctionengine.service

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.mapper.SnapshotAuction
import net.jonasmf.auctionengine.mapper.toModifierLinkRows
import net.jonasmf.auctionengine.mapper.toSnapshotAuction
import net.jonasmf.auctionengine.mapper.toUpsertRow
import net.jonasmf.auctionengine.repository.rds.AuctionJDBCRepository
import net.jonasmf.auctionengine.utility.JvmRuntimeDiagnostics
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.NestedRuntimeException
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.sql.SQLException
import java.time.OffsetDateTime
import java.time.ZonedDateTime

data class AuctionSnapshotPersistenceSummary(
    val processedAuctions: Int,
    val batchCount: Int,
    val softDeletedAuctions: Int,
)

@Service
class AuctionSnapshotPersistenceService(
    private val auctionJdbcRepository: AuctionJDBCRepository,
    private val updateHistoryService: ConnectedRealmUpdateHistoryService,
) {
    private val logger: Logger = LoggerFactory.getLogger(AuctionSnapshotPersistenceService::class.java)
    private val snapshotBatchSize = 5_000
    private val deadlockRetryAttempts = 3
    private val deadlockRetryBaseDelayMs = 100L

    fun saveSnapshot(
        payloadPath: Path,
        connectedRealm: ConnectedRealm,
        auctionCount: Int,
        lastModified: ZonedDateTime,
    ): AuctionSnapshotPersistenceSummary {
        val snapshotStartTime = System.currentTimeMillis()
        val updateHistory = updateHistoryService.startUpdate(connectedRealm, auctionCount, lastModified)
        var processedAuctions = 0
        var batchCount = 0

        logger.info(
            "Persisting current auction snapshot for realm {} auctions={} {}",
            connectedRealm.id,
            auctionCount,
            JvmRuntimeDiagnostics.snapshot(),
        )

        streamAuctionBatches(payloadPath) { batch ->
            batchCount++
            withDeadlockRetry("persist auction snapshot batch", connectedRealm.id) {
                persistBatch(
                    auctions = batch,
                    connectedRealmId = connectedRealm.id,
                    updateHistoryId = updateHistory.id,
                    snapshotTime = lastModified.toOffsetDateTime(),
                )
            }
            processedAuctions += batch.size
            logger.info(
                "Persisted auction snapshot batch {}/? for realm {} processed={}/{} {}",
                batchCount,
                connectedRealm.id,
                processedAuctions,
                auctionCount,
                JvmRuntimeDiagnostics.snapshot(),
            )
        }

        val softDeletedAuctions =
            withDeadlockRetry("mark missing auctions deleted", connectedRealm.id) {
                auctionJdbcRepository.markMissingAuctionsDeleted(
                    connectedRealmId = connectedRealm.id,
                    updateHistoryId = updateHistory.id,
                    deletedAt = lastModified.toOffsetDateTime(),
                )
            }
        withDeadlockRetry("mark update history completed", connectedRealm.id) {
            updateHistoryService.setUpdateToCompleted(connectedRealm.id, lastModified)
        }

        logger.info(
            "Completed auction snapshot persistence for realm {} in {}ms batches={} processed={} softDeleted={} {}",
            connectedRealm.id,
            System.currentTimeMillis() - snapshotStartTime,
            batchCount,
            processedAuctions,
            softDeletedAuctions,
            JvmRuntimeDiagnostics.snapshot(),
        )

        return AuctionSnapshotPersistenceSummary(
            processedAuctions = processedAuctions,
            batchCount = batchCount,
            softDeletedAuctions = softDeletedAuctions,
        )
    }

    fun saveAuction(
        auction: AuctionDTO,
        connectedRealm: ConnectedRealm,
        lastModified: ZonedDateTime = ZonedDateTime.now(),
    ) {
        val updateHistory = updateHistoryService.startUpdate(connectedRealm, 1, lastModified)
        withDeadlockRetry("persist single auction snapshot", connectedRealm.id) {
            persistBatch(
                auctions = listOf(auction.toSnapshotAuction()),
                connectedRealmId = connectedRealm.id,
                updateHistoryId = updateHistory.id,
                snapshotTime = lastModified.toOffsetDateTime(),
            )
        }
        withDeadlockRetry("mark update history completed", connectedRealm.id) {
            updateHistoryService.setUpdateToCompleted(connectedRealm.id, lastModified)
        }
    }

    fun deleteSoftDeletedAuctionsOlderThan(cutoff: OffsetDateTime): Int =
        auctionJdbcRepository.deleteSoftDeletedAuctionsOlderThan(cutoff)

    private fun persistBatch(
        auctions: List<SnapshotAuction>,
        connectedRealmId: Int,
        updateHistoryId: Long,
        snapshotTime: OffsetDateTime,
    ) {
        if (auctions.isEmpty()) return

        val itemVariants =
            auctions
                .map(SnapshotAuction::itemVariant)
                .distinctBy { it.variantHash }
                .sortedBy { it.variantHash }
        val modifierRows = itemVariants.flatMap { variant -> variant.modifiers }.distinct().map { it.toUpsertRow() }
        auctionJdbcRepository.upsertModifiers(modifierRows)
        val modifierIds =
            auctionJdbcRepository
                .findModifierIds(modifierRows)
                .mapKeys { (modifier, _) ->
                    net.jonasmf.auctionengine.mapper.AuctionModifierKey(
                        type = modifier.type,
                        value = modifier.value,
                    )
                }

        val itemRows = itemVariants.map { it.toUpsertRow() }
        auctionJdbcRepository.upsertAuctionItems(itemRows)
        val itemIds = auctionJdbcRepository.findAuctionItemIds(itemVariants.map { it.variantHash })

        val linkRows =
            itemVariants.flatMap { variant ->
                variant.toModifierLinkRows(
                    auctionItemId = itemIds.getValue(variant.variantHash),
                    modifierIds = modifierIds,
                )
            }
        auctionJdbcRepository.upsertAuctionItemModifierLinks(linkRows)

        val auctionRows =
            auctions.sortedBy(SnapshotAuction::auctionId).map { auction ->
                auction.toUpsertRow(
                    connectedRealmId = connectedRealmId,
                    auctionItemId = itemIds.getValue(auction.itemVariant.variantHash),
                    updateHistoryId = updateHistoryId,
                    snapshotTime = snapshotTime,
                )
            }
        auctionJdbcRepository.upsertAuctions(auctionRows)
    }

    private fun <T> withDeadlockRetry(
        operation: String,
        connectedRealmId: Int,
        block: () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (failure: RuntimeException) {
                if (!isDeadlockFailure(failure) || attempt >= deadlockRetryAttempts) {
                    throw failure
                }
                val delayMs = deadlockRetryBaseDelayMs * attempt
                logger.warn(
                    "Deadlock while attempting to {} for realm {}. Retrying attempt {}/{} after {}ms",
                    operation,
                    connectedRealmId,
                    attempt + 1,
                    deadlockRetryAttempts,
                    delayMs,
                )
                Thread.sleep(delayMs)
                attempt++
            }
        }
    }

    private fun isDeadlockFailure(failure: RuntimeException): Boolean {
        val mostSpecificCause =
            when (failure) {
                is NestedRuntimeException -> failure.mostSpecificCause
                else -> failure
            }
        return when {
            mostSpecificCause is SQLException ->
                mostSpecificCause.sqlState == "40001" ||
                    mostSpecificCause.errorCode == 1213 ||
                    mostSpecificCause.message?.contains(
                        "Deadlock found when trying to get lock",
                        ignoreCase = true,
                    ) == true
            failure is DataAccessException ->
                failure.message?.contains(
                    "Deadlock found when trying to get lock",
                    ignoreCase = true,
                ) == true
            else -> false
        }
    }

    private fun streamAuctionBatches(
        payloadPath: Path,
        consumer: (List<SnapshotAuction>) -> Unit,
    ) {
        val mapper = jacksonObjectMapper()
        val jsonFactory = JsonFactory(mapper)
        payloadPath.toFile().inputStream().use { input ->
            jsonFactory.createParser(input).use { parser ->
                require(parser.nextToken() == JsonToken.START_OBJECT) {
                    "Auction payload root must be a JSON object"
                }
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    val fieldName = parser.currentName
                    parser.nextToken()
                    if (fieldName == "auctions") {
                        require(parser.currentToken == JsonToken.START_ARRAY) {
                            "Auction payload field 'auctions' must be an array"
                        }
                        val batch = mutableListOf<SnapshotAuction>()
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            batch += mapper.readValue(parser, AuctionDTO::class.java).toSnapshotAuction()
                            if (batch.size >= snapshotBatchSize) {
                                consumer(batch.toList())
                                batch.clear()
                            }
                        }
                        if (batch.isNotEmpty()) {
                            consumer(batch.toList())
                        }
                    } else {
                        parser.skipChildren()
                    }
                }
            }
        }
    }
}
