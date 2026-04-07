package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.converters.toKotlin
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionData
import net.jonasmf.auctionengine.dto.auction.AuctionDataResponse
import net.jonasmf.auctionengine.integration.blizzard.BlizzardAuctionApiClient
import net.jonasmf.auctionengine.repository.rds.AuctionItemModifierRepository
import net.jonasmf.auctionengine.repository.rds.AuctionItemRepository
import net.jonasmf.auctionengine.repository.rds.AuctionRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min
import net.jonasmf.auctionengine.dbo.rds.auction.Auction as AuctionDBO
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionItem as AuctionItemDBO
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionItemModifier as AuctionItemModifierDBO

@Service
class BlizzardAuctionService(
    private val properties: BlizzardApiProperties,
    private val blizzardAuctionApiClient: BlizzardAuctionApiClient,
    private val authService: AuthService,
    private val amazonS3: AmazonS3Service,
    private val auctionRepository: AuctionRepository,
    private val auctionItemRepository: AuctionItemRepository,
    private val hourlyPriceStatisticsService: HourlyPriceStatisticsService,
    private val realmService: ConnectedRealmService,
    private val auctionItemModifierRepository: AuctionItemModifierRepository,
    private val updateHistoryService: ConnectedRealmUpdateHistoryService,
    private val auctionHouseService: AuctionHouseService,
) {
    private val auctionBatchSize = 100_000
    private val logBatchSize = auctionBatchSize
    val logger: Logger = LoggerFactory.getLogger(BlizzardAuctionService::class.java)

    fun updateAuctionHouses(
        region: Region,
        auctionHousesToUpdate: List<AuctionHouseDynamo>,
    ) {
        val batchStartTime = System.currentTimeMillis()
        logger.info("Updating ${auctionHousesToUpdate.size} auction houses for region {}", region)
        auctionHousesToUpdate.forEach {
            val houseStartTime = System.currentTimeMillis()
            logger.info("Starting sequential update for auction house {}", it.connectedId)
            updateHouse(it.connectedId, region)
            logger.info(
                "Finished sequential update for auction house {} in {}ms",
                it.connectedId,
                System.currentTimeMillis() - houseStartTime,
            )
        }
        logger.info(
            "Finished updating {} auction houses for region {} in {}ms",
            auctionHousesToUpdate.size,
            region,
            System.currentTimeMillis() - batchStartTime,
        )
    }

    private fun updateHouse(
        connectedRealmId: Int,
        region: Region,
    ) {
        val startTime = System.currentTimeMillis()
        logger.debug("Starting update for house: connectedRealmId={}, region={}", connectedRealmId, region)
        try {
            val response =
                blizzardAuctionApiClient
                    .getLatestAuctionDump(connectedRealmId, region)
                    .block() ?: run {
                    logger.error("Latest dump path lookup returned no response for realm {}", connectedRealmId)
                    return
                }

            val connectedRealm = realmService.getById(connectedRealmId)
            if (connectedRealm == null) {
                logger.error("ConnectedRealm not found for id $connectedRealmId")
                return
            }

            val house = connectedRealm.auctionHouse
            // TODO: Cleanup so that the original lastModified also is Instant
            val lastModified =
                ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(response.lastModified),
                    TimeZone.getDefault().toZoneId(),
                )

            if (house.lastModified == null || lastModified.isAfter(house.lastModified)) {
                logger.info("New auction data available for $connectedRealmId. Last modified: $lastModified")
                saveDumpPathToS3(region, connectedRealmId, response)
                processAuctionData(response.url, region, connectedRealm, connectedRealmId, lastModified)
            } else {
                logger.debug(
                    "No new auction data available for {}. Current: {}, Latest: {}",
                    connectedRealmId,
                    house.lastModified,
                    lastModified,
                )
                auctionHouseService.updateTimes(
                    // TODO: Cleanup so that the original lastModified also is Instant
                    connectedRealmId,
                    lastModified.toInstant().toKotlin(),
                    false,
                )
            }
        } catch (error: Exception) {
            logger.error(
                "Failed to get latest dump path for realm $connectedRealmId after ${System.currentTimeMillis() - startTime}ms",
                error,
            )
        }
    }

    private fun processAuctionData(
        url: String,
        region: Region,
        connectedRealm: ConnectedRealm,
        connectedRealmId: Int,
        lastModified: ZonedDateTime,
    ) {
        val startTime = System.currentTimeMillis()
        logger.info("Starting auction data processing for realm $connectedRealmId")
        try {
            val data =
                blizzardAuctionApiClient
                    .downloadAuctionData(url)
                    .block() ?: run {
                    logger.error("Auction data download returned no payload for realm {}", connectedRealmId)
                    auctionHouseService.updateTimes(
                        connectedRealmId,
                        lastModified.toInstant().toKotlin(),
                        false,
                    )
                    return
                }

            val auctionCount = data.auctions.size
            logger.info(
                "Fetched auction data for $connectedRealmId: $auctionCount auctions in ${System.currentTimeMillis() - startTime}ms",
            )

            if (auctionCount == 0) {
                logger.warn("No auctions found for realm $connectedRealmId")
                return
            }

            val s3Url = saveAuctionDataToS3(region, connectedRealmId, data, lastModified)
            if (s3Url == null) {
                logger.error("Auction payload archive failed for realm {}. Marking update as failed.", connectedRealmId)
                auctionHouseService.updateTimes(
                    connectedRealmId,
                    lastModified.toInstant().toKotlin(),
                    false,
                )
                return
            }
            hourlyPriceStatisticsService.processHourlyPriceStatistics(connectedRealm, data.auctions, lastModified)
            auctionHouseService.updateTimes(
                connectedRealmId,
                lastModified.toInstant().toKotlin(),
                true,
                s3Url,
            )
            /* Disabled, as we don't really need ALL auctions in the database as it takes up a lot of space
                And processing is also really slow for "100k-400k" auctions and all it's corresponding data.
                I might get back to this later and see if I can optimize it more, but for now I'm more interested in trends over time
                and not having every single auction ever recorded in the database.
            saveAuctionsToDatabase(
                connectedRealm,
                auctionCount,
                lastModified,
                data,
                connectedRealmId,
                startTime,
                url,
            )*/
        } catch (error: Exception) {
            logger.error(
                "Failed to fetch auction data for realm $connectedRealmId after ${System.currentTimeMillis() - startTime}ms",
                error,
            )
            auctionHouseService.updateTimes(
                // TODO: Cleanup so that the original lastModified also is Instant
                connectedRealmId,
                lastModified.toInstant().toKotlin(),
                false,
            )
        }
    }

    private fun saveAuctionDataToS3(
        region: Region,
        connectedRealmId: Int,
        data: AuctionData,
        lastModified: ZonedDateTime,
    ): String? {
        var s3Url: String? = null
        if (data.auctions.isEmpty()) {
            logger.warn("No auction data to upload for realm $connectedRealmId")
            return s3Url
        }
        val lastModifiedMs = lastModified.toInstant().toEpochMilli()
        val startTime = System.currentTimeMillis()
        val filePath = "auctions/${region.name.lowercase(Locale.getDefault())}/${
            if (connectedRealmId < 0) "commodity" else "$connectedRealmId"
        }/$lastModifiedMs.json"

        try {
            s3Url = amazonS3.uploadFile(region, filePath, data)
            logger.debug("Successfully uploaded auctions to S3: $filePath")
        } catch (e: Exception) {
            logger.error("Failed to upload auctions to S3: $filePath", e)
        }
        if (s3Url != null) {
            logger.info(
                "Uploaded auctions to S3 for $connectedRealmId: ${data.auctions.size} auctions in ${System.currentTimeMillis() - startTime}ms",
            )
        }
        return s3Url
    }

    private fun saveDumpPathToS3(
        region: Region,
        connectedRealmId: Int,
        response: AuctionDataResponse,
    ) {
        val filePath = "auctions/${region.name.lowercase(
            Locale.getDefault(),
        )}/${if (connectedRealmId < 0) "commodity" else "$connectedRealmId"}/dump-path.json"

        try {
            amazonS3.uploadFile(region, filePath, response)
            logger.debug("Successfully uploaded dump path to S3: $filePath")
        } catch (e: Exception) {
            logger.error("Failed to upload dump path to S3: $filePath", e)
        }
    }

    /**
     * Saves auctions to the database in batches, along with associated items and modifiers.
     * Also updates the latest dump timestamp and marks the update as completed.
     */
    private fun saveAuctionsToDatabase(
        connectedRealm: ConnectedRealm,
        auctionCount: Int,
        lastModified: ZonedDateTime,
        data: AuctionData?,
        connectedRealmId: Int,
        startTime: Long,
        url: String,
    ) {
        if (data == null || data.auctions.isEmpty()) {
            logger.warn("No auction data to process for realm $connectedRealmId")
            return
        }
        try {
            val updateHistory = updateHistoryService.startUpdate(connectedRealm, auctionCount, lastModified)
            processAuctionsInBatches(data.auctions, connectedRealm, connectedRealmId, updateHistory, startTime)
            realmService.updateLatestDump(connectedRealmId, lastModified)
            updateHistoryService.setUpdateToCompleted(connectedRealmId, lastModified)
            logger.info(
                "Successfully processed $auctionCount auctions for $connectedRealmId in ${System.currentTimeMillis() - startTime}ms",
            )
        } catch (e: Exception) {
            logger.error("Failed to process auction data for realm $connectedRealmId", e)
            authService.getToken().subscribe { token ->
                logger.error("Processing failed for URL: $url&access_token=$token", e)
            }
        }
    }

    @Transactional
    fun processAuctionsInBatches(
        auctions: List<AuctionDTO>,
        connectedRealm: ConnectedRealm,
        connectedRealmId: Int,
        updateHistory: ConnectedRealmUpdateHistory,
        startTime: Long,
    ) {
        val totalAuctions = auctions.size
        logger.info("Processing $totalAuctions auctions in batches of $auctionBatchSize for realm $connectedRealmId")

        try {
            val processedItems = mutableMapOf<String, AuctionItemDBO>()
            val newItems = mutableListOf<AuctionItemDBO>()
            var duplicateItemCount = 0
            val totalAuctions = auctions.size

            auctions.forEachIndexed { index, auctionDTO ->
                val itemKey = createItemKey(auctionDTO.item)

                if (processedItems.containsKey(itemKey)) {
                    return@forEachIndexed
                }
                val existingItems =
                    auctionItemRepository.findByCompositeKeyWithNullHandlingList(
                        auctionDTO.item.id,
                        auctionDTO.item.pet_breed_id,
                        auctionDTO.item.pet_level,
                        auctionDTO.item.pet_quality_id,
                        auctionDTO.item.pet_species_id,
                        auctionDTO.item.context,
                    )

                val existingItem =
                    if (existingItems.isNotEmpty()) {
                        if (existingItems.size > 1) {
                            duplicateItemCount++
                            logger.debug(
                                "Found ${existingItems.size} duplicate items for itemId=${auctionDTO.item.id}, petBreedId=${auctionDTO.item.pet_breed_id}, petLevel=${auctionDTO.item.pet_level}, petQualityId=${auctionDTO.item.pet_quality_id}, petSpeciesId=${auctionDTO.item.pet_species_id}, context=${auctionDTO.item.context}. Using first match.",
                            )
                        }
                        existingItems.first()
                    } else {
                        null
                    }

                if (existingItem != null) {
                    processedItems[itemKey] = existingItem
                    logger.debug("Found existing item for key: $itemKey")
                } else {
                    val newItem = auctionDTO.item.toDBO()
                    newItems.add(newItem)
                    processedItems[itemKey] = newItem
                    logger.debug("Created new item for key: $itemKey")
                }

                // logger progress every 10,000 items processed
                if ((index + 1) % 10000 == 0) {
                    val progress = ((index + 1) * 100.0 / totalAuctions).toInt()
                    logger.info(
                        "Item processing progress: ${index + 1}/$totalAuctions ($progress%) - $duplicateItemCount duplicate items found so far",
                    )
                }
            }

            logger.info(
                "Found ${processedItems.size} unique items (${newItems.size} new, ${processedItems.size - newItems.size} existing) for realm $connectedRealmId",
            )
            if (duplicateItemCount > 0) {
                logger.warn(
                    "Found $duplicateItemCount items with duplicate entries in database - this indicates data quality issues that should be investigated",
                )
            }

            if (newItems.isNotEmpty()) {
                val itemsStartTime = System.currentTimeMillis()
                saveItemsInBatches(newItems, "items")
                val itemsElapsed = System.currentTimeMillis() - itemsStartTime
                logger.info(
                    "Successfully saved ${newItems.size} new items for realm $connectedRealmId in ${itemsElapsed}ms",
                )
            }

            val auctionMappingStartTime = System.currentTimeMillis()
            val auctionDbos =
                auctions.map { auctionDTO ->
                    val itemKey = createItemKey(auctionDTO.item)
                    val item = processedItems[itemKey]
                    if (item == null) {
                        throw IllegalStateException("Item not found for key: $itemKey")
                    }

                    auctionDTO.toDBO(connectedRealm, updateHistory).copy(item = item)
                }
            val auctionMappingElapsed = System.currentTimeMillis() - auctionMappingStartTime
            logger.info("Mapped ${auctionDbos.size} auctions for realm $connectedRealmId in ${auctionMappingElapsed}ms")

            logger.info("Processing ${auctionDbos.size} auctions for realm $connectedRealmId")
            saveAuctionsInBatches(auctionDbos, connectedRealm, connectedRealmId, updateHistory, startTime)

            val modifiersExtractionStartTime = System.currentTimeMillis()
            val modifiers =
                auctionDbos.flatMap { auction ->
                    auction.item.modifiers ?: emptyList()
                }
            val modifiersExtractionElapsed = System.currentTimeMillis() - modifiersExtractionStartTime
            logger.info(
                "Extracted ${modifiers.size} item modifiers for realm $connectedRealmId in ${modifiersExtractionElapsed}ms",
            )

            if (modifiers.isNotEmpty()) {
                logger.info("Processing ${modifiers.size} item modifiers for realm $connectedRealmId")
                saveModifiersInBatches(modifiers, "modifiers")
            }

            logger.info("Successfully completed processing for realm $connectedRealmId")
        } catch (e: Exception) {
            logger.error("Failed to process auctions for realm $connectedRealmId", e)
            throw e
        }
    }

    private fun createItemKey(item: net.jonasmf.auctionengine.dto.auction.AuctionItemDTO): String =
        "${item.id}_${item.pet_breed_id ?: "null"}_${item.pet_level ?: "null"}_${item.pet_quality_id ?: "null"}_${item.pet_species_id ?: "null"}_${item.context ?: "null"}_${item.modifiers?.hashCode() ?: "null"}"

    private fun saveItemsInBatches(
        items: List<AuctionItemDBO>,
        itemType: String,
    ) {
        val batches = items.chunked(auctionBatchSize)
        val totalStartTime = System.currentTimeMillis()
        logger.debug("Saving ${items.size} $itemType in ${batches.size} batches")

        batches.forEachIndexed { index, batch ->
            val batchStartTime = System.currentTimeMillis()
            try {
                logger.debug("Saving batch ${index + 1}/${batches.size} with ${batch.size} $itemType")
                val savedItems = auctionItemRepository.saveAll(batch)
                val batchElapsed = System.currentTimeMillis() - batchStartTime
                val batchRate = if (batchElapsed > 0) (savedItems.size * 1000.0 / batchElapsed).toInt() else 0
                logger.debug(
                    "Successfully saved batch ${index + 1} with ${savedItems.size} $itemType in ${batchElapsed}ms ($batchRate $itemType/sec)",
                )

                if ((index + 1) % 10 == 0 || index == batches.size - 1) {
                    val totalElapsed = System.currentTimeMillis() - totalStartTime
                    val totalRate =
                        if (totalElapsed >
                            0
                        ) {
                            ((index + 1) * auctionBatchSize * 1000.0 / totalElapsed).toInt()
                        } else {
                            0
                        }
                    val progress = min((index + 1) * auctionBatchSize, items.size)
                    logger.info(
                        "Saved $progress/${items.size} $itemType in ${totalElapsed}ms ($totalRate $itemType/sec)",
                    )
                }
            } catch (e: Exception) {
                val batchElapsed = System.currentTimeMillis() - batchStartTime
                logger.error("Failed to save batch ${index + 1} of $itemType after ${batchElapsed}ms", e)
                throw e
            }
        }

        val totalElapsed = System.currentTimeMillis() - totalStartTime
        val totalRate = if (totalElapsed > 0) (items.size * 1000.0 / totalElapsed).toInt() else 0
        logger.info("Completed saving ${items.size} $itemType in ${totalElapsed}ms ($totalRate $itemType/sec)")
    }

    private fun saveAuctionsInBatches(
        auctionDbos: List<AuctionDBO>,
        connectedRealm: ConnectedRealm,
        connectedRealmId: Int,
        updateHistory: ConnectedRealmUpdateHistory,
        startTime: Long,
    ) {
        val batches = auctionDbos.chunked(auctionBatchSize)
        logger.debug("Saving ${auctionDbos.size} auctions in ${batches.size} batches for realm $connectedRealmId")

        batches.forEachIndexed { index, batch ->
            val batchStartTime = System.currentTimeMillis()
            try {
                logger.debug(
                    "Saving auction batch ${index + 1}/${batches.size} with ${batch.size} auctions for realm $connectedRealmId",
                )

                // Use batch upsert for maximum performance - process in smaller chunks to avoid memory issues
                val chunkSize = 1000
                val chunks = batch.chunked(chunkSize)
                var totalProcessed = 0
                var totalDuplicates = 0

                chunks.forEach { chunk ->
                    val chunkStartTime = System.currentTimeMillis()
                    var chunkProcessed = 0
                    var chunkDuplicates = 0

                    chunk.forEach { auction ->
                        try {
                            val upsertedCount =
                                auctionRepository.upsertAuction(
                                    id = auction.id.id,
                                    connectedRealmId = auction.id.connectedRealm.id,
                                    itemId =
                                        auction.item.id
                                            ?: throw IllegalStateException(
                                                "Auction item missing identifier for auction ${auction.id.id}",
                                            ),
                                    quantity = auction.quantity,
                                    unitPrice = auction.unitPrice,
                                    timeLeft = auction.timeLeft.ordinal,
                                    buyout = auction.buyout,
                                    firstSeen = auction.firstSeen,
                                    lastSeen = auction.lastSeen,
                                    updateHistoryId = updateHistory.id,
                                )

                            if (upsertedCount > 0) {
                                chunkProcessed++
                            } else {
                                chunkDuplicates++
                            }
                        } catch (individualException: Exception) {
                            logger.warn(
                                "Failed to upsert auction ${auction.id.id} for realm $connectedRealmId: ${individualException.message}",
                            )
                            chunkDuplicates++
                        }
                    }

                    val chunkElapsed = System.currentTimeMillis() - chunkStartTime
                    val chunkRate = if (chunkElapsed > 0) (chunk.size * 1000.0 / chunkElapsed).toInt() else 0
                    logger.debug(
                        "Processed chunk with ${chunk.size} auctions: $chunkProcessed saved, $chunkDuplicates duplicates for realm $connectedRealmId in ${chunkElapsed}ms ($chunkRate auctions/sec)",
                    )

                    totalProcessed += chunkProcessed
                    totalDuplicates += chunkDuplicates
                }

                val batchElapsed = System.currentTimeMillis() - batchStartTime
                val batchRate = if (batchElapsed > 0) (batch.size * 1000.0 / batchElapsed).toInt() else 0
                logger.debug(
                    "Successfully processed auction batch ${index + 1} with ${batch.size} auctions: $totalProcessed saved, $totalDuplicates duplicates for realm $connectedRealmId in ${batchElapsed}ms ($batchRate auctions/sec)",
                )

                val processedCount = (index + 1) * auctionBatchSize
                val progress = min(processedCount, auctionDbos.size)

                if (progress % logBatchSize == 0 || index == batches.size - 1) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val rate = (progress * 1000.0 / elapsed).toInt()
                    logger.info(
                        "Realm $connectedRealmId: Processed $progress/${auctionDbos.size} auctions ($rate auctions/sec)",
                    )
                }
            } catch (e: Exception) {
                val batchElapsed = System.currentTimeMillis() - batchStartTime
                logger.error(
                    "Failed to save auction batch ${index + 1} for realm $connectedRealmId after ${batchElapsed}ms",
                    e,
                )
                throw e
            }
        }
    }

    private fun saveModifiersInBatches(
        modifiers: List<AuctionItemModifierDBO>,
        itemType: String,
    ) {
        if (modifiers.isEmpty()) return

        val batches = modifiers.chunked(auctionBatchSize)
        val totalStartTime = System.currentTimeMillis()
        logger.debug("Saving ${modifiers.size} $itemType in ${batches.size} batches")

        batches.forEachIndexed { index, batch ->
            val batchStartTime = System.currentTimeMillis()
            try {
                logger.debug("Saving modifier batch ${index + 1}/${batches.size} with ${batch.size} $itemType")
                val savedModifiers = auctionItemModifierRepository.saveAll(batch)
                val batchElapsed = System.currentTimeMillis() - batchStartTime
                val batchRate = if (batchElapsed > 0) (savedModifiers.size * 1000.0 / batchElapsed).toInt() else 0
                logger.debug(
                    "Successfully saved modifier batch ${index + 1} with ${savedModifiers.size} $itemType in ${batchElapsed}ms ($batchRate $itemType/sec)",
                )

                if ((index + 1) % 10 == 0 || index == batches.size - 1) {
                    val totalElapsed = System.currentTimeMillis() - totalStartTime
                    val totalRate =
                        if (totalElapsed >
                            0
                        ) {
                            ((index + 1) * auctionBatchSize * 1000.0 / totalElapsed).toInt()
                        } else {
                            0
                        }
                    val progress = min((index + 1) * auctionBatchSize, modifiers.size)
                    logger.info(
                        "Saved $progress/${modifiers.size} $itemType in ${totalElapsed}ms ($totalRate $itemType/sec)",
                    )
                }
            } catch (e: Exception) {
                val batchElapsed = System.currentTimeMillis() - batchStartTime
                logger.error("Failed to save modifier batch ${index + 1} of $itemType after ${batchElapsed}ms", e)
                throw e
            }
        }

        val totalElapsed = System.currentTimeMillis() - totalStartTime
        val totalRate = if (totalElapsed > 0) (modifiers.size * 1000.0 / totalElapsed).toInt() else 0
        logger.info("Completed saving ${modifiers.size} $itemType in ${totalElapsed}ms ($totalRate $itemType/sec)")
    }

    @Transactional
    fun saveAuction(
        auction: AuctionDTO,
        connectedRealm: ConnectedRealm,
    ) {
        try {
            val updateHistory = updateHistoryService.startUpdate(connectedRealm, 1, ZonedDateTime.now())
            val auctionDbo = auction.toDBO(connectedRealm, updateHistory)

            auctionDbo.item.modifiers?.forEach { modifier ->
                auctionItemModifierRepository.save(modifier)
            }
            val savedItem = auctionItemRepository.save(auctionDbo.item)

            auctionRepository.upsertAuction(
                id = auctionDbo.id.id,
                connectedRealmId = connectedRealm.id,
                itemId =
                    savedItem.id ?: throw IllegalStateException(
                        "Failed to persist auction item for ${auction.id}",
                    ),
                quantity = auctionDbo.quantity,
                unitPrice = auctionDbo.unitPrice,
                timeLeft = auctionDbo.timeLeft.ordinal,
                buyout = auctionDbo.buyout,
                firstSeen = auctionDbo.firstSeen,
                lastSeen = auctionDbo.lastSeen,
                updateHistoryId = updateHistory.id,
            )

            logger.debug("Successfully upserted auction ${auctionDbo.id.id} for item ${savedItem.id}")
        } catch (e: Exception) {
            if (e.message?.contains("Duplicate entry") == true) {
                logger.debug("Skipping duplicate auction ${auction.id} for realm ${connectedRealm.id}")
                return
            }
            logger.error("Failed to save auction: ${auction.id}", e)
            throw e
        }
    }
}
