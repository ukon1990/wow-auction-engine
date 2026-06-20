package net.jonasmf.auctionengine.service

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.transaction.Transactional
import net.jonasmf.auctionengine.dbo.rds.auction.Auction
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionPrice
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.mapper.FlatAuction
import net.jonasmf.auctionengine.mapper.toAuctionPriceDBO
import net.jonasmf.auctionengine.mapper.toDBO
import net.jonasmf.auctionengine.mapper.toFlatObject
import net.jonasmf.auctionengine.repository.rds.AuctionJDBCRepository
import net.jonasmf.auctionengine.utility.JvmRuntimeDiagnostics
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.ZonedDateTime
import kotlin.collections.mutableListOf
import kotlin.math.roundToInt

data class AuctionSnapshotPersistenceSummary(
    val processedAuctions: Int,
    val uniqueItems: Int,
    val groupedResult: Pair<MutableList<Auction>, MutableList<AuctionPrice>>,
    val updateHistory: ConnectedRealmUpdateHistory,
)

@Service
class AuctionSnapshotPersistenceService(
    private val auctionJdbcRepository: AuctionJDBCRepository,
    private val updateHistoryService: ConnectedRealmUpdateHistoryService,
) {
    private val logger: Logger = LoggerFactory.getLogger(AuctionSnapshotPersistenceService::class.java)

    // TODO: Consider, if I should return the values and process for readability, or process each batch imminently to save memory

    /**
     * Processes the grouped auctions into batches that are then upserted.
     * Prices and auctions are split pre insert.
     * The percentile prices at 25 and 75 percentile are calculated.
     *
     * I want this to be blocking, as I don't want to overload the database as It's shared with other users.
     * So I am intentionally not parallelizing.
     */
    private fun mapToAuctionAndPriceArrayBatchesAndReduceMap(
        auctions: MutableMap<String, MutableList<FlatAuction>>,
        updateHistory: ConnectedRealmUpdateHistory,
        connectedRealm: ConnectedRealm,
    ): Pair<MutableList<Auction>, MutableList<AuctionPrice>> {
        val auctionBatches = mutableListOf<Auction>()
        val priceBatches = mutableListOf<AuctionPrice>()

        auctions.forEach { uniqueAuctionItem ->
            val value = uniqueAuctionItem.value
            val auction = value.first().toDBO(connectedRealm, updateHistory)
            val prices =
                uniqueAuctionItem.value
                    .map { auctionItem ->
                        if (auctionItem.bid != null && (auction.bid == null || auctionItem.bid!! < auction.bid!!)) {
                            auction.bid = auctionItem.bid
                        }
                        val priceItem = auctionItem.toAuctionPriceDBO(updateHistory.lastModified?.toInstant())
                        priceItem.auction = auction
                        priceItem
                    }
            val pricedEntries = prices.filter { it.buyout != null }.sortedBy { it.buyout }
            if (pricedEntries.isNotEmpty()) {
                val priceEntryMaxIndex = (pricedEntries.size - 1).toFloat()
                val percentile25Index = (priceEntryMaxIndex * 0.25f).roundToInt()
                val percentile75Index = (priceEntryMaxIndex * 0.75f).roundToInt()
                auction.buyout = pricedEntries.first().buyout
                auction.p25 = pricedEntries[percentile25Index].buyout
                auction.p75 = pricedEntries[percentile75Index].buyout
            } else {
                auction.buyout = null
                auction.p25 = null
                auction.p75 = null
            }

            auctionBatches.add(auction)
            priceBatches.addAll(prices)
        }

        return Pair(auctionBatches, priceBatches)
    }

    @Transactional
    fun saveSnapshot(
        payloadPath: Path,
        connectedRealm: ConnectedRealm,
        lastModified: ZonedDateTime,
    ): AuctionSnapshotPersistenceSummary {
        val snapshotStartTime = System.currentTimeMillis()
        // TODO: Remember to update the count part. Not sure if we need to save that in the db
        val auctionCount = 0
        val updateHistory = updateHistoryService.startUpdate(connectedRealm, auctionCount, lastModified)

        logger.info(
            "Persisting current auction snapshot for realm {} auctions={} {}",
            connectedRealm.id,
            auctionCount,
            JvmRuntimeDiagnostics.snapshot(),
        )
        val groupedFlatAuctions = streamAndReturnGroupedAuction(payloadPath, connectedRealm.id)
        val uniqueItemCount = groupedFlatAuctions.size
        val groupedResult =
            mapToAuctionAndPriceArrayBatchesAndReduceMap(
                auctions = groupedFlatAuctions,
                connectedRealm = connectedRealm,
                updateHistory = updateHistory,
            )
        auctionJdbcRepository.upsertAuctions(groupedResult.first)
        auctionJdbcRepository.upsertAuctionPrices(groupedResult.second, updateHistory)
        logger.info(
            "Grouped auctions into {} items for realm {} {}",
            uniqueItemCount,
            connectedRealm.id,
            JvmRuntimeDiagnostics.snapshot(),
        )
        updateHistoryService.setUpdateToCompleted(updateHistory.id)

        logger.info(
            "Completed auction snapshot persistence for realm {} in {}ms processed={} {}",
            connectedRealm.id,
            System.currentTimeMillis() - snapshotStartTime,
            0,
            JvmRuntimeDiagnostics.snapshot(),
        )

        return AuctionSnapshotPersistenceSummary(
            processedAuctions = groupedResult.second.size, // TODO: Fix propper count
            uniqueItems = uniqueItemCount,
            groupedResult = groupedResult,
            updateHistory = updateHistory,
        )
    }

    // TODO: Split into smaller pieces pre merge

    /**
     * Streaming in the auctions from file on disk to save memory and creating objects with a smaller memory footprint.
     * Not calculating values like p25 and p75 etc here, as we need to have processed/grouped all the auctions first
     */
    private fun streamAndReturnGroupedAuction(
        payloadPath: Path,
        connectedRealmId: Int,
    ): MutableMap<String, MutableList<FlatAuction>> {
        val groupedAuctions = mutableMapOf<String, MutableList<FlatAuction>>()
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
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            val flatAuction =
                                mapper
                                    .readValue(
                                        parser,
                                        AuctionDTO::class.java,
                                    ).toFlatObject(connectedRealmId)
                            if (groupedAuctions[flatAuction.id] == null) {
                                groupedAuctions[flatAuction.id] = mutableListOf()
                            }
                            groupedAuctions[flatAuction.id]?.add(flatAuction)
                        }
                    } else {
                        parser.skipChildren()
                    }
                }
            }
        }
        return groupedAuctions
    }
}
