package net.jonasmf.auctionengine.service

import NameSpace
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionData
import net.jonasmf.auctionengine.dto.auction.AuctionDataResponse
import net.jonasmf.auctionengine.repository.rds.AuctionItemModifierRepository
import net.jonasmf.auctionengine.repository.rds.AuctionItemRepository
import net.jonasmf.auctionengine.repository.rds.AuctionRepository
import net.jonasmf.auctionengine.utility.determineBaseUrl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
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
    private val webClientWithAuth: WebClient,
    private val authService: AuthService,
    private val amazonS3: AmazonS3Service,
    private val auctionRepository: AuctionRepository,
    private val auctionItemRepository: AuctionItemRepository,
    private val hourlyPriceStatisticsService: HourlyPriceStatisticsService,
    private val realmService: ConnectedRealmService,
    private val auctionItemModifierRepository: AuctionItemModifierRepository,
    private val updateHistoryService: ConnectedRealmUpdateHistoryService,
) {
    private val webClient: WebClient = webClientWithAuth

    companion object {
        private const val AUCTION_BATCH_SIZE = 100_000
        private const val LOG_BATCH_SIZE = AUCTION_BATCH_SIZE // Log progress every 10k auctions
        val LOG: Logger = LoggerFactory.getLogger(BlizzardAuctionService::class.java)
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelay = 3_000)
    fun checkForUpdates() {
        LOG.info("Starting scheduled auction house update check...")
        updateAuctionHouses()
    }

    fun updateAuctionHouses() {
        val ids = listOf(-3, 1403)
        LOG.info("Updating auction houses for realm IDs: $ids")
        ids.forEach { updateHouse(it, Region.Europe) }
    }

    private fun updateHouse(
        connectedRealmId: Int,
        region: Region,
    ) {
        LOG.debug("Starting update for house: connectedRealmId=$connectedRealmId, region=$region")

        getLatestDumpPath(connectedRealmId, region).subscribe(
            { response ->
                val connectedRealm = realmService.getById(connectedRealmId)
                if (connectedRealm == null) {
                    LOG.error("ConnectedRealm not found for id $connectedRealmId")
                    return@subscribe
                }

                val house = connectedRealm.auctionHouse
                val lastModified =
                    ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(response.lastModified),
                        TimeZone.getDefault().toZoneId(),
                    )

                if (house.lastModified == null || lastModified.isAfter(house.lastModified)) {
                    LOG.info("New auction data available for $connectedRealmId. Last modified: $lastModified")
                    processAuctionData(response.url, region, connectedRealm, connectedRealmId, lastModified)
                } else {
                    LOG.debug(
                        "No new auction data available for $connectedRealmId. Current: ${house.lastModified}, Latest: $lastModified",
                    )
                }
            },
            { error ->
                LOG.error("Failed to get latest dump path for realm $connectedRealmId", error)
            },
        )
    }

    private fun processAuctionData(
        url: String,
        region: Region,
        connectedRealm: ConnectedRealm,
        connectedRealmId: Int,
        lastModified: ZonedDateTime,
    ) {
        val startTime = System.currentTimeMillis()
        LOG.info("Starting auction data processing for realm $connectedRealmId")

        getAuctionData(url, region).subscribe(
            { data ->
                val auctionCount = data.auctions.size
                LOG.info(
                    "Fetched auction data for $connectedRealmId: $auctionCount auctions in ${System.currentTimeMillis() - startTime}ms",
                )

                if (auctionCount == 0) {
                    LOG.warn("No auctions found for realm $connectedRealmId")
                    return@subscribe
                }

                saveAuctionDataToS3(region, connectedRealmId, data, lastModified)
                hourlyPriceStatisticsService.processHourlyPriceStatistics(connectedRealm, data.auctions, lastModified)

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
            },
            { error ->
                LOG.error("Failed to fetch auction data for realm $connectedRealmId", error)
            },
        )
    }

    private fun saveAuctionDataToS3(
        region: Region,
        connectedRealmId: Int,
        data: AuctionData,
        lastModified: ZonedDateTime,
    ) {
        if (data.auctions.isEmpty()) {
            LOG.warn("No auction data to upload for realm $connectedRealmId")
            return
        }
        val startTime = System.currentTimeMillis()
        val filePath = "auctions/${region.name.lowercase(Locale.getDefault())}/${
            if (connectedRealmId < 0) "commodity" else "$connectedRealmId"
        }/$lastModified.json"

        try {
            amazonS3.uploadFile(region, filePath, data)
            LOG.debug("Successfully uploaded auctions to S3: $filePath")
        } catch (e: Exception) {
            LOG.error("Failed to upload auctions to S3: $filePath", e)
        }
        LOG.info(
            "Uploaded auctions to S3 for $connectedRealmId: ${data.auctions.size} auctions in ${System.currentTimeMillis() - startTime}ms",
        )
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
            LOG.warn("No auction data to process for realm $connectedRealmId")
            return
        }
        try {
            val updateHistory = updateHistoryService.startUpdate(connectedRealm, auctionCount, lastModified)
            processAuctionsInBatches(data.auctions, connectedRealm, connectedRealmId, updateHistory, startTime)
            realmService.updateLatestDump(connectedRealmId, lastModified)
            updateHistoryService.setUpdateToCompleted(connectedRealmId, lastModified)
            LOG.info(
                "Successfully processed $auctionCount auctions for $connectedRealmId in ${System.currentTimeMillis() - startTime}ms",
            )
        } catch (e: Exception) {
            LOG.error("Failed to process auction data for realm $connectedRealmId", e)
            authService.getToken().subscribe { token ->
                LOG.error("Processing failed for URL: $url&access_token=$token", e)
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
        LOG.info("Processing $totalAuctions auctions in batches of $AUCTION_BATCH_SIZE for realm $connectedRealmId")

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
                            LOG.debug(
                                "Found ${existingItems.size} duplicate items for itemId=${auctionDTO.item.id}, petBreedId=${auctionDTO.item.pet_breed_id}, petLevel=${auctionDTO.item.pet_level}, petQualityId=${auctionDTO.item.pet_quality_id}, petSpeciesId=${auctionDTO.item.pet_species_id}, context=${auctionDTO.item.context}. Using first match.",
                            )
                        }
                        existingItems.first()
                    } else {
                        null
                    }

                if (existingItem != null) {
                    processedItems[itemKey] = existingItem
                    LOG.debug("Found existing item for key: $itemKey")
                } else {
                    val newItem = auctionDTO.item.toDBO()
                    newItems.add(newItem)
                    processedItems[itemKey] = newItem
                    LOG.debug("Created new item for key: $itemKey")
                }

                // Log progress every 10,000 items processed
                if ((index + 1) % 10000 == 0) {
                    val progress = ((index + 1) * 100.0 / totalAuctions).toInt()
                    LOG.info(
                        "Item processing progress: ${index + 1}/$totalAuctions ($progress%) - $duplicateItemCount duplicate items found so far",
                    )
                }
            }

            LOG.info(
                "Found ${processedItems.size} unique items (${newItems.size} new, ${processedItems.size - newItems.size} existing) for realm $connectedRealmId",
            )
            if (duplicateItemCount > 0) {
                LOG.warn(
                    "Found $duplicateItemCount items with duplicate entries in database - this indicates data quality issues that should be investigated",
                )
            }

            if (newItems.isNotEmpty()) {
                val itemsStartTime = System.currentTimeMillis()
                saveItemsInBatches(newItems, "items")
                val itemsElapsed = System.currentTimeMillis() - itemsStartTime
                LOG.info(
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
            LOG.info("Mapped ${auctionDbos.size} auctions for realm $connectedRealmId in ${auctionMappingElapsed}ms")

            LOG.info("Processing ${auctionDbos.size} auctions for realm $connectedRealmId")
            saveAuctionsInBatches(auctionDbos, connectedRealm, connectedRealmId, updateHistory, startTime)

            val modifiersExtractionStartTime = System.currentTimeMillis()
            val modifiers =
                auctionDbos.flatMap { auction ->
                    auction.item.modifiers ?: emptyList()
                }
            val modifiersExtractionElapsed = System.currentTimeMillis() - modifiersExtractionStartTime
            LOG.info(
                "Extracted ${modifiers.size} item modifiers for realm $connectedRealmId in ${modifiersExtractionElapsed}ms",
            )

            if (modifiers.isNotEmpty()) {
                LOG.info("Processing ${modifiers.size} item modifiers for realm $connectedRealmId")
                saveModifiersInBatches(modifiers, "modifiers")
            }

            LOG.info("Successfully completed processing for realm $connectedRealmId")
        } catch (e: Exception) {
            LOG.error("Failed to process auctions for realm $connectedRealmId", e)
            throw e
        }
    }

    private fun createItemKey(item: net.jonasmf.auctionengine.dto.auction.AuctionItemDTO): String =
        "${item.id}_${item.pet_breed_id ?: "null"}_${item.pet_level ?: "null"}_${item.pet_quality_id ?: "null"}_${item.pet_species_id ?: "null"}_${item.context ?: "null"}_${item.modifiers?.hashCode() ?: "null"}"

    private fun saveItemsInBatches(
        items: List<AuctionItemDBO>,
        itemType: String,
    ) {
        val batches = items.chunked(AUCTION_BATCH_SIZE)
        val totalStartTime = System.currentTimeMillis()
        LOG.debug("Saving ${items.size} $itemType in ${batches.size} batches")

        batches.forEachIndexed { index, batch ->
            val batchStartTime = System.currentTimeMillis()
            try {
                LOG.debug("Saving batch ${index + 1}/${batches.size} with ${batch.size} $itemType")
                val savedItems = auctionItemRepository.saveAll(batch)
                val batchElapsed = System.currentTimeMillis() - batchStartTime
                val batchRate = if (batchElapsed > 0) (savedItems.size * 1000.0 / batchElapsed).toInt() else 0
                LOG.debug(
                    "Successfully saved batch ${index + 1} with ${savedItems.size} $itemType in ${batchElapsed}ms ($batchRate $itemType/sec)",
                )

                if ((index + 1) % 10 == 0 || index == batches.size - 1) {
                    val totalElapsed = System.currentTimeMillis() - totalStartTime
                    val totalRate =
                        if (totalElapsed >
                            0
                        ) {
                            ((index + 1) * AUCTION_BATCH_SIZE * 1000.0 / totalElapsed).toInt()
                        } else {
                            0
                        }
                    val progress = min((index + 1) * AUCTION_BATCH_SIZE, items.size)
                    LOG.info("Saved $progress/${items.size} $itemType in ${totalElapsed}ms ($totalRate $itemType/sec)")
                }
            } catch (e: Exception) {
                val batchElapsed = System.currentTimeMillis() - batchStartTime
                LOG.error("Failed to save batch ${index + 1} of $itemType after ${batchElapsed}ms", e)
                throw e
            }
        }

        val totalElapsed = System.currentTimeMillis() - totalStartTime
        val totalRate = if (totalElapsed > 0) (items.size * 1000.0 / totalElapsed).toInt() else 0
        LOG.info("Completed saving ${items.size} $itemType in ${totalElapsed}ms ($totalRate $itemType/sec)")
    }

    private fun saveAuctionsInBatches(
        auctionDbos: List<AuctionDBO>,
        connectedRealm: ConnectedRealm,
        connectedRealmId: Int,
        updateHistory: ConnectedRealmUpdateHistory,
        startTime: Long,
    ) {
        val batches = auctionDbos.chunked(AUCTION_BATCH_SIZE)
        LOG.debug("Saving ${auctionDbos.size} auctions in ${batches.size} batches for realm $connectedRealmId")

        batches.forEachIndexed { index, batch ->
            val batchStartTime = System.currentTimeMillis()
            try {
                LOG.debug(
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
                            LOG.warn(
                                "Failed to upsert auction ${auction.id.id} for realm $connectedRealmId: ${individualException.message}",
                            )
                            chunkDuplicates++
                        }
                    }

                    val chunkElapsed = System.currentTimeMillis() - chunkStartTime
                    val chunkRate = if (chunkElapsed > 0) (chunk.size * 1000.0 / chunkElapsed).toInt() else 0
                    LOG.debug(
                        "Processed chunk with ${chunk.size} auctions: $chunkProcessed saved, $chunkDuplicates duplicates for realm $connectedRealmId in ${chunkElapsed}ms ($chunkRate auctions/sec)",
                    )

                    totalProcessed += chunkProcessed
                    totalDuplicates += chunkDuplicates
                }

                val batchElapsed = System.currentTimeMillis() - batchStartTime
                val batchRate = if (batchElapsed > 0) (batch.size * 1000.0 / batchElapsed).toInt() else 0
                LOG.debug(
                    "Successfully processed auction batch ${index + 1} with ${batch.size} auctions: $totalProcessed saved, $totalDuplicates duplicates for realm $connectedRealmId in ${batchElapsed}ms ($batchRate auctions/sec)",
                )

                val processedCount = (index + 1) * AUCTION_BATCH_SIZE
                val progress = min(processedCount, auctionDbos.size)

                if (progress % LOG_BATCH_SIZE == 0 || index == batches.size - 1) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val rate = (progress * 1000.0 / elapsed).toInt()
                    LOG.info(
                        "Realm $connectedRealmId: Processed $progress/${auctionDbos.size} auctions ($rate auctions/sec)",
                    )
                }
            } catch (e: Exception) {
                val batchElapsed = System.currentTimeMillis() - batchStartTime
                LOG.error(
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

        val batches = modifiers.chunked(AUCTION_BATCH_SIZE)
        val totalStartTime = System.currentTimeMillis()
        LOG.debug("Saving ${modifiers.size} $itemType in ${batches.size} batches")

        batches.forEachIndexed { index, batch ->
            val batchStartTime = System.currentTimeMillis()
            try {
                LOG.debug("Saving modifier batch ${index + 1}/${batches.size} with ${batch.size} $itemType")
                val savedModifiers = auctionItemModifierRepository.saveAll(batch)
                val batchElapsed = System.currentTimeMillis() - batchStartTime
                val batchRate = if (batchElapsed > 0) (savedModifiers.size * 1000.0 / batchElapsed).toInt() else 0
                LOG.debug(
                    "Successfully saved modifier batch ${index + 1} with ${savedModifiers.size} $itemType in ${batchElapsed}ms ($batchRate $itemType/sec)",
                )

                if ((index + 1) % 10 == 0 || index == batches.size - 1) {
                    val totalElapsed = System.currentTimeMillis() - totalStartTime
                    val totalRate =
                        if (totalElapsed >
                            0
                        ) {
                            ((index + 1) * AUCTION_BATCH_SIZE * 1000.0 / totalElapsed).toInt()
                        } else {
                            0
                        }
                    val progress = min((index + 1) * AUCTION_BATCH_SIZE, modifiers.size)
                    LOG.info(
                        "Saved $progress/${modifiers.size} $itemType in ${totalElapsed}ms ($totalRate $itemType/sec)",
                    )
                }
            } catch (e: Exception) {
                val batchElapsed = System.currentTimeMillis() - batchStartTime
                LOG.error("Failed to save modifier batch ${index + 1} of $itemType after ${batchElapsed}ms", e)
                throw e
            }
        }

        val totalElapsed = System.currentTimeMillis() - totalStartTime
        val totalRate = if (totalElapsed > 0) (modifiers.size * 1000.0 / totalElapsed).toInt() else 0
        LOG.info("Completed saving ${modifiers.size} $itemType in ${totalElapsed}ms ($totalRate $itemType/sec)")
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

            LOG.debug("Successfully upserted auction ${auctionDbo.id.id} for item ${savedItem.id}")
        } catch (e: Exception) {
            if (e.message?.contains("Duplicate entry") == true) {
                LOG.debug("Skipping duplicate auction ${auction.id} for realm ${connectedRealm.id}")
                return
            }
            LOG.error("Failed to save auction: ${auction.id}", e)
            throw e
        }
    }

    fun getAuctionData(
        url: String,
        region: Region,
        gameBuild: GameBuildVersion = GameBuildVersion.RETAIL,
    ): Mono<AuctionData> {
        val isClassic = gameBuild == GameBuildVersion.CLASSIC
        val namespace = if (isClassic) NameSpace.DYNAMIC_CLASSIC else NameSpace.DYNAMIC_RETAIL

        LOG.debug("Fetching auction data from: $url, region: $region, gameBuild: $gameBuild")

        return authService.getToken().flatMap { token ->
            webClient
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(AuctionData::class.java)
                .doOnNext {
                    LOG.info("Successfully fetched auction data from $url")
                }.doOnError { error ->
                    LOG.error("Failed to fetch auction data from $url: ${error.message}", error)
                }
        }
    }

    fun getLatestDumpPath(
        id: Int,
        region: Region,
        gameBuild: GameBuildVersion = GameBuildVersion.RETAIL,
        previousLastModified: ZonedDateTime? = null,
    ): Mono<AuctionDataResponse> {
        val isClassic = gameBuild == GameBuildVersion.CLASSIC
        val path =
            if (id <
                0
            ) {
                "auctions/commodities"
            } else {
                "connected-realm/$id/auctions${if (isClassic) "/index" else ""}"
            }

        if (isClassic) {
            LOG.info("Classic is not supported for now")
        }

        val namespace =
            when (region) {
                Region.NorthAmerica -> NameSpace.DYNAMIC_US
                Region.Europe -> NameSpace.DYNAMIC_EU
                Region.Korea -> NameSpace.DYNAMIC_KR
                Region.Taiwan -> NameSpace.DYNAMIC_TW
            }

        LOG.debug("Getting latest dump path for id: $id, region: $region, path: $path")

        return authService.getToken().flatMap { token ->
            val baseUrl = determineBaseUrl(region, properties)
            val url = "${baseUrl}$path?namespace=${namespace.value}&locale=en_US"

            webClient
                .get()
                .uri(url)
                .header("If-Modified-Since", "Sat, 14 Mar 3000 20:07:10 GMT")
                .retrieve()
                .toEntity(String::class.java)
                .map { httpResponse ->
                    val headers = httpResponse.headers
                    val lastModified = headers.lastModified
                    val lastModifiedZonedDate =
                        ZonedDateTime.ofInstant(
                            Instant.ofEpochMilli(lastModified ?: 0),
                            TimeZone.getDefault().toZoneId(),
                        )
                    val cleanedUrl = url.replace("access_token=$token&", "")

                    val response =
                        AuctionDataResponse(
                            lastModified = lastModified ?: 0,
                            url = cleanedUrl,
                            gameBuild = gameBuild,
                        )

                    if (previousLastModified == null || previousLastModified.isBefore(lastModifiedZonedDate)) {
                        val filePath = "auctions/${region.name.lowercase(Locale.getDefault())}/${
                            if (id < 0) "commodity" else "$id"
                        }/dump-path.json"

                        try {
                            amazonS3.uploadFile(region, filePath, response)
                            LOG.debug("Successfully uploaded dump path to S3: $filePath")
                        } catch (e: Exception) {
                            LOG.error("Failed to upload dump path to S3: $filePath", e)
                        }
                    }

                    response
                }.doOnNext {
                    LOG.info("Successfully fetched latest dump path with last modified: ${it.lastModified}")
                }.doOnError { error ->
                    LOG.error("Failed to fetch latest dump path from $url: ${error.message}", error)
                }
        }
    }
}
