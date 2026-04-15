package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.converters.toKotlin
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionDataResponse
import net.jonasmf.auctionengine.integration.blizzard.BlizzardApiClientException
import net.jonasmf.auctionengine.integration.blizzard.BlizzardAuctionApiClient
import net.jonasmf.auctionengine.integration.blizzard.DownloadedAuctionPayload
import net.jonasmf.auctionengine.utility.JvmRuntimeDiagnostics
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone
import kotlin.io.path.deleteIfExists

@Service
class BlizzardAuctionService(
    private val properties: BlizzardApiProperties,
    private val blizzardAuctionApiClient: BlizzardAuctionApiClient,
    private val amazonS3: AmazonS3Service,
    private val hourlyPriceStatisticsService: HourlyPriceStatisticsService,
    private val realmService: ConnectedRealmService,
    private val auctionSnapshotPersistenceService: AuctionSnapshotPersistenceService,
    private val auctionHouseService: AuctionHouseService,
    private val runtimeHealthTracker: RuntimeHealthTracker,
) {
    val logger: Logger = LoggerFactory.getLogger(BlizzardAuctionService::class.java)

    fun updateAuctionHouses(
        region: Region,
        auctionHousesToUpdate: List<AuctionHouse>,
    ) {
        val batchStartTime = System.currentTimeMillis()
        logger.info(
            "Updating {} auction houses for region {} {}",
            auctionHousesToUpdate.size,
            region,
            JvmRuntimeDiagnostics.snapshot(),
        )
        auctionHousesToUpdate.forEach {
            val houseStartTime = System.currentTimeMillis()
            logger.info("Starting sequential update for auction house {}", it.connectedId)
            runtimeHealthTracker.markUpdateBatchProgress(
                "fetch-dump-metadata",
                region = region,
                connectedRealmId = it.connectedId,
            )
            updateHouse(it.connectedId, region)
            logger.info(
                "Finished sequential update for auction house {} in {}ms {}",
                it.connectedId,
                System.currentTimeMillis() - houseStartTime,
                JvmRuntimeDiagnostics.snapshot(),
            )
        }
        logger.info(
            "Finished updating {} auction houses for region {} in {}ms {}",
            auctionHousesToUpdate.size,
            region,
            System.currentTimeMillis() - batchStartTime,
            JvmRuntimeDiagnostics.snapshot(),
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
                    markAuctionUpdateFailed(connectedRealmId)
                    return
                }

            val connectedRealm = realmService.getById(connectedRealmId)
            if (connectedRealm == null) {
                logger.error("ConnectedRealm not found for id $connectedRealmId")
                markAuctionUpdateFailed(connectedRealmId)
                return
            }

            val house = connectedRealm.auctionHouse
            // TODO: Cleanup so that the original lastModified also is Instant
            val lastModifiedInstant = Instant.ofEpochMilli(response.lastModified)
            val lastModified =
                ZonedDateTime.ofInstant(
                    lastModifiedInstant,
                    TimeZone.getDefault().toZoneId(),
                )

            if (house.lastModified == null || lastModifiedInstant.isAfter(house.lastModified)) {
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
            logAuctionUpdateFailure(
                connectedRealmId = connectedRealmId,
                startTime = startTime,
                failure = error,
                action = "get latest dump path",
            )
            markAuctionUpdateFailed(connectedRealmId)
        }
    }

    private fun markAuctionUpdateFailed(
        connectedRealmId: Int,
        lastModified: ZonedDateTime? = null,
    ) {
        auctionHouseService.updateTimes(
            connectedRealmId,
            lastModified?.toInstant()?.toKotlin(),
            false,
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
        logger.info(
            "Starting auction data processing for realm {} region {} {}",
            connectedRealmId,
            region,
            JvmRuntimeDiagnostics.snapshot(),
        )
        try {
            runtimeHealthTracker.markUpdateBatchProgress(
                "download-auction-payload",
                region = region,
                connectedRealmId = connectedRealmId,
            )
            val downloadStartTime = System.currentTimeMillis()
            val downloadedPayload =
                blizzardAuctionApiClient
                    .downloadAuctionData(url)
                    .block() ?: run {
                    logger.error("Auction data download returned no payload for realm {}", connectedRealmId)
                    markAuctionUpdateFailed(connectedRealmId, lastModified)
                    return
                }
            try {
                logger.info(
                    "Fetched auction data for realm {} region {} into {} in {}ms {}",
                    connectedRealmId,
                    region,
                    downloadedPayload.path,
                    System.currentTimeMillis() - downloadStartTime,
                    JvmRuntimeDiagnostics.snapshot(),
                )
                runtimeHealthTracker.markUpdateBatchProgress(
                    "archive-auction-payload",
                    region = region,
                    connectedRealmId = connectedRealmId,
                )
                val s3ArchiveStartTime = System.currentTimeMillis()
                val s3Url = saveAuctionDataToS3(region, connectedRealmId, downloadedPayload, lastModified)
                if (s3Url == null) {
                    logger.error(
                        "Auction payload archive failed for realm {}. Marking update as failed.",
                        connectedRealmId,
                    )
                    markAuctionUpdateFailed(connectedRealmId, lastModified)
                    return
                }
                logger.info(
                    "Archived auction payload for realm {} region {} in {}ms {}",
                    connectedRealmId,
                    region,
                    System.currentTimeMillis() - s3ArchiveStartTime,
                    JvmRuntimeDiagnostics.snapshot(),
                )
                runtimeHealthTracker.markUpdateBatchProgress(
                    "aggregate-hourly-stats",
                    region = region,
                    connectedRealmId = connectedRealmId,
                )
                val hourlyStatsStartTime = System.currentTimeMillis()
                val hourlyStatsSummary =
                    hourlyPriceStatisticsService.processHourlyPriceStatisticsFromFile(
                        connectedRealm = connectedRealm,
                        payloadPath = downloadedPayload.path,
                        lastModified = lastModified,
                    )
                if (hourlyStatsSummary.processedAuctions == 0) {
                    logger.warn("No auctions found for realm {}", connectedRealmId)
                    markAuctionUpdateFailed(connectedRealmId, lastModified)
                    return
                }
                logger.info(
                    "Completed hourly stats processing for realm {} region {} auctions={} groupedRows={} insertedRows={} in {}ms {}",
                    connectedRealmId,
                    region,
                    hourlyStatsSummary.processedAuctions,
                    hourlyStatsSummary.groupedRows,
                    hourlyStatsSummary.insertedRows,
                    System.currentTimeMillis() - hourlyStatsStartTime,
                    JvmRuntimeDiagnostics.snapshot(),
                )
                runtimeHealthTracker.markUpdateBatchProgress(
                    "persist-current-auctions",
                    region = region,
                    connectedRealmId = connectedRealmId,
                )
                val snapshotPersistenceStartTime = System.currentTimeMillis()
                val snapshotSummary =
                    saveAuctionsToDatabase(
                        connectedRealm = connectedRealm,
                        auctionCount = hourlyStatsSummary.processedAuctions,
                        lastModified = lastModified,
                        payloadPath = downloadedPayload.path,
                        connectedRealmId = connectedRealmId,
                    )
                logger.info(
                    "Completed current auction persistence for realm {} region {} auctions={} batches={} softDeleted={} in {}ms {}",
                    connectedRealmId,
                    region,
                    snapshotSummary.processedAuctions,
                    snapshotSummary.batchCount,
                    snapshotSummary.softDeletedAuctions,
                    System.currentTimeMillis() - snapshotPersistenceStartTime,
                    JvmRuntimeDiagnostics.snapshot(),
                )
                runtimeHealthTracker.markUpdateBatchProgress(
                    "update-auction-house-times",
                    region = region,
                    connectedRealmId = connectedRealmId,
                )
                auctionHouseService.updateTimes(
                    connectedRealmId,
                    lastModified.toInstant().toKotlin(),
                    true,
                    s3Url,
                )
            } finally {
                downloadedPayload.path.deleteIfExists()
            }
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
            logAuctionUpdateFailure(
                connectedRealmId = connectedRealmId,
                startTime = startTime,
                failure = error,
                action = "process auction data",
            )
            runtimeHealthTracker.markUpdateBatchProgress(
                "auction-processing-failed",
                region = region,
                connectedRealmId = connectedRealmId,
            )
            markAuctionUpdateFailed(connectedRealmId, lastModified)
        }
    }

    private fun logAuctionUpdateFailure(
        connectedRealmId: Int,
        startTime: Long,
        failure: Exception,
        action: String,
    ) {
        val elapsedMs = System.currentTimeMillis() - startTime
        if (failure is BlizzardApiClientException) {
            logger.warn(
                "Failed to {} for realm {} after {}ms: {}",
                action,
                connectedRealmId,
                elapsedMs,
                failure.summary,
            )
            logger.debug(
                "Auction update diagnostics for realm {} while attempting to {}",
                connectedRealmId,
                action,
                failure,
            )
            return
        }

        logger.error(
            "Failed to {} for realm {} after {}ms: {}",
            action,
            connectedRealmId,
            elapsedMs,
            failure.message ?: failure::class.simpleName ?: "unknown error",
            failure,
        )
    }

    private fun saveAuctionDataToS3(
        region: Region,
        connectedRealmId: Int,
        payload: DownloadedAuctionPayload,
        lastModified: ZonedDateTime,
    ): String? {
        var s3Url: String? = null
        val lastModifiedMs = lastModified.toInstant().toEpochMilli()
        val startTime = System.currentTimeMillis()
        val filePath = "auctions/${region.name.lowercase(Locale.getDefault())}/${
            if (connectedRealmId < 0) "commodity" else "$connectedRealmId"
        }/$lastModifiedMs.json"

        try {
            s3Url = amazonS3.uploadCompressedFile(region, filePath, payload.path)
            logger.debug("Successfully uploaded auctions to S3: $filePath")
        } catch (e: Exception) {
            logger.error("Failed to upload auctions to S3: $filePath", e)
        }
        if (s3Url != null) {
            logger.info(
                "Uploaded auctions to S3 for realm {} region {} from {} (contentLength={}B) in {}ms {}",
                connectedRealmId,
                region,
                payload.path,
                payload.contentLength ?: "unknown",
                System.currentTimeMillis() - startTime,
                JvmRuntimeDiagnostics.snapshot(),
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

    private fun saveAuctionsToDatabase(
        connectedRealm: ConnectedRealm,
        auctionCount: Int,
        lastModified: ZonedDateTime,
        payloadPath: java.nio.file.Path,
        connectedRealmId: Int,
    ): AuctionSnapshotPersistenceSummary {
        if (auctionCount <= 0) {
            logger.warn("No auction data to process for realm {}", connectedRealmId)
            return AuctionSnapshotPersistenceSummary(processedAuctions = 0, batchCount = 0, softDeletedAuctions = 0)
        }
        return auctionSnapshotPersistenceService.saveSnapshot(
            payloadPath = payloadPath,
            connectedRealm = connectedRealm,
            auctionCount = auctionCount,
            lastModified = lastModified,
        )
    }

    fun saveAuction(
        auction: AuctionDTO,
        connectedRealm: ConnectedRealm,
    ) {
        auctionSnapshotPersistenceService.saveAuction(auction, connectedRealm)
        logger.debug("Successfully upserted auction {} for realm {}", auction.id, connectedRealm.id)
    }
}
