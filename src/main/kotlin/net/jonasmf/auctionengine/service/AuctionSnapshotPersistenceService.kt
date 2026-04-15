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
import org.springframework.stereotype.Service
import java.nio.file.Path
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
            persistBatch(
                auctions = batch,
                connectedRealmId = connectedRealm.id,
                updateHistoryId = updateHistory.id,
                snapshotTime = lastModified.toOffsetDateTime(),
            )
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
            auctionJdbcRepository.markMissingAuctionsDeleted(
                connectedRealmId = connectedRealm.id,
                updateHistoryId = updateHistory.id,
                deletedAt = lastModified.toOffsetDateTime(),
            )
        updateHistoryService.setUpdateToCompleted(connectedRealm.id, lastModified)

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
        persistBatch(
            auctions = listOf(auction.toSnapshotAuction()),
            connectedRealmId = connectedRealm.id,
            updateHistoryId = updateHistory.id,
            snapshotTime = lastModified.toOffsetDateTime(),
        )
        updateHistoryService.setUpdateToCompleted(connectedRealm.id, lastModified)
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

        val itemVariants = auctions.map(SnapshotAuction::itemVariant).distinctBy { it.variantHash }
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
            auctions.map { auction ->
                auction.toUpsertRow(
                    connectedRealmId = connectedRealmId,
                    auctionItemId = itemIds.getValue(auction.itemVariant.variantHash),
                    updateHistoryId = updateHistoryId,
                    snapshotTime = snapshotTime,
                )
            }
        auctionJdbcRepository.upsertAuctions(auctionRows)
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
