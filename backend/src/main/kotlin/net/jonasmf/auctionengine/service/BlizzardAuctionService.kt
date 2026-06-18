package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.domain.realm.AuctionHouse
import net.jonasmf.auctionengine.integration.blizzard.BlizzardApiClientException
import net.jonasmf.auctionengine.integration.blizzard.BlizzardAuctionApiClient
import net.jonasmf.auctionengine.repository.rds.AuctionStatsHourlyJDBCRepository
import net.jonasmf.auctionengine.utility.JvmRuntimeDiagnostics
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.NestedRuntimeException
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.sql.SQLException
import java.time.Instant
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.io.path.deleteIfExists
import kotlin.time.toKotlinInstant

@Service
class BlizzardAuctionService(
    private val properties: BlizzardApiProperties,
    private val blizzardAuctionApiClient: BlizzardAuctionApiClient,
    private val auctionStatsHourlyService: AuctionStatsHourlyService,
    private val realmService: ConnectedRealmService,
    private val auctionSnapshotPersistenceService: AuctionSnapshotPersistenceService,
    private val auctionHouseService: AuctionHouseService,
    private val runtimeHealthTracker: RuntimeHealthTracker,
    private val auctionStatsHourlyJDBCRepository: AuctionStatsHourlyJDBCRepository,
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
                    lastModified.toInstant().toKotlinInstant(),
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
            lastModified?.toInstant()?.toKotlinInstant(),
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
                    "aggregate-hourly-stats",
                    region = region,
                    connectedRealmId = connectedRealmId,
                )
                val auctionInsertStart = System.currentTimeMillis()

                val auctionSaveSummary =
                    auctionSnapshotPersistenceService.saveSnapshot(
                        connectedRealm = connectedRealm,
                        lastModified = lastModified,
                        payloadPath = downloadedPayload.path,
                    )
                logger.info(
                    "Completed auction price inserts stats processing for realm {} region {} auctions={} in {}ms {}",
                    connectedRealmId,
                    region,
                    auctionSaveSummary.processedAuctions,
                    System.currentTimeMillis() - auctionInsertStart,
                    JvmRuntimeDiagnostics.snapshot(),
                )

                auctionStatsHourlyService.updateHourlyStatsForRealm(
                    connectedRealm = connectedRealm,
                    lastModified = lastModified,
                    connectedRealmUpdateHistoryId = auctionSaveSummary.updateHistory.id,
                )

                runtimeHealthTracker.markUpdateBatchProgress(
                    "update-auction-house-times",
                    region = region,
                    connectedRealmId = connectedRealmId,
                )
                auctionHouseService.updateTimes(
                    connectedRealmId,
                    lastModified.toInstant().toKotlinInstant(),
                    true,
                )
            } finally {
                downloadedPayload.path.deleteIfExists()
            }
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

        val failureSummary = summarizeAuctionUpdateFailure(failure)
        val logMessage = "Failed to {} for realm {} after {}ms: {}"
        if (failureSummary.warnOnly) {
            logger.warn(
                logMessage,
                action,
                connectedRealmId,
                elapsedMs,
                failureSummary.message,
            )
        } else {
            logger.error(
                logMessage,
                action,
                connectedRealmId,
                elapsedMs,
                failureSummary.message,
            )
        }
        logger.debug(
            "Auction update diagnostics for realm {} while attempting to {}",
            connectedRealmId,
            action,
            failure,
        )
    }

    private fun summarizeAuctionUpdateFailure(failure: Exception): AuctionUpdateFailureSummary {
        val mostSpecificCause =
            when (failure) {
                is NestedRuntimeException -> failure.mostSpecificCause
                else -> failure
            }

        if (mostSpecificCause is SQLException) {
            return summarizeSqlFailure(mostSpecificCause)
        }
        if (failure is DataAccessException) {
            return AuctionUpdateFailureSummary(
                message = sanitizeFailureMessage(failure.message ?: failure::class.simpleName ?: "data access error"),
                warnOnly = false,
            )
        }
        return AuctionUpdateFailureSummary(
            message = sanitizeFailureMessage(failure.message ?: failure::class.simpleName ?: "unknown error"),
            warnOnly = false,
        )
    }

    private fun summarizeSqlFailure(sqlException: SQLException): AuctionUpdateFailureSummary {
        val sanitizedMessage =
            sanitizeFailureMessage(
                sqlException.message ?: sqlException::class.simpleName ?: "SQL error",
            )
        val sqlStateSuffix = sqlException.sqlState?.takeIf { it.isNotBlank() }?.let { " sqlState=$it" } ?: ""
        val vendorCodeSuffix = sqlException.errorCode.takeIf { it != 0 }?.let { " vendorCode=$it" } ?: ""
        val isDeadlock =
            sqlException.sqlState == "40001" ||
                sqlException.errorCode == 1213 ||
                sanitizedMessage.contains("Deadlock found when trying to get lock", ignoreCase = true)

        return if (isDeadlock) {
            AuctionUpdateFailureSummary(
                message = "database deadlock$sqlStateSuffix$vendorCodeSuffix: $sanitizedMessage",
                warnOnly = true,
            )
        } else {
            AuctionUpdateFailureSummary(
                message = "database error$sqlStateSuffix$vendorCodeSuffix: $sanitizedMessage",
                warnOnly = false,
            )
        }
    }

    private fun sanitizeFailureMessage(message: String): String =
        message
            .replace(Regex(""";\s*SQL\s*\[.*$""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""\b(?:PreparedStatement|Statement|CallableStatement)Callback;?\s*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private data class AuctionUpdateFailureSummary(
        val message: String,
        val warnOnly: Boolean,
    )
}
