package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.RealmDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.StatsDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.converters.toKotlin
import net.jonasmf.auctionengine.dbo.rds.FileReference
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouseFileLog
import net.jonasmf.auctionengine.domain.AuctionHouseUpdateLog
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime
import java.util.Optional
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import net.jonasmf.auctionengine.domain.AuctionHouse as AuctionHouseDomain
import net.jonasmf.auctionengine.repository.AuctionHouseRepository as AuctionHouseStateRepository
import net.jonasmf.auctionengine.repository.AuctionHouseUpdateLogRepository as AuctionHouseLogRepository

@Repository
class AuctionHouseStateRepositoryImpl(
    private val auctionHouseRepository: AuctionHouseRepository,
    private val connectedRealmRepository: ConnectedRealmRepository,
    private val auctionHouseUpdateLogRepository: AuctionHouseLogRepository,
) : AuctionHouseStateRepository {
    override fun findById(id: Int?): Optional<AuctionHouseDomain> {
        if (id == null) {
            return Optional.empty()
        }

        val entity =
            auctionHouseRepository.findByConnectedId(id).orElse(null)
                ?: connectedRealmRepository.findById(id).orElse(null)?.auctionHouse
                ?: return Optional.empty()
        return Optional.of(entity.toDomain(resolveRealms(id)))
    }

    override fun findAllByRegion(region: Region): List<AuctionHouseDomain> =
        auctionHouseRepository.findAllByRegion(region).map { it.toDomain(resolveRealms(it.connectedId)) }

    override fun findReadyForUpdateByRegion(region: Region): List<AuctionHouseDomain> =
        auctionHouseRepository
            .findAllByRegionAndNextUpdateLessThanEqualOrderByNextUpdateAsc(
                region,
                java.time.Instant.now(),
                PageRequest.of(0, 50),
            ).map { it.toDomain(resolveRealms(it.connectedId)) }

    override fun save(auctionHouse: AuctionHouseDomain): AuctionHouseDomain {
        requireNotNull(auctionHouse.id) { "AuctionHouse.id must not be null when saving to MariaDB" }
        requireNotNull(auctionHouse.lastModified) { "AuctionHouse.lastModified cannot be null when saving to MariaDB" }

        val connectedId = auctionHouse.connectedId.takeIf { it != 0 } ?: auctionHouse.id!!
        val connectedRealm = connectedRealmRepository.findById(connectedId).orElse(null)
        val existing = auctionHouseRepository.findByConnectedId(connectedId).orElse(null)
        val entity =
            if (existing != null) {
                existing.applyDomain(auctionHouse)
            } else if (connectedRealm != null) {
                connectedRealm.auctionHouse.applyDomain(auctionHouse)
            } else {
                AuctionHouse().applyDomain(auctionHouse)
            }

        val saved =
            if (connectedRealm != null && connectedRealm.auctionHouse.id != entity.id) {
                connectedRealm.auctionHouse = entity
                connectedRealmRepository.save(connectedRealm).auctionHouse
            } else {
                auctionHouseRepository.save(entity)
            }

        val fileSize = saved.auctionFile?.size ?: auctionHouse.size
        val fileUrl = saved.auctionFile?.path ?: auctionHouse.url
        auctionHouseUpdateLogRepository.save(connectedId, auctionHouse.lastModified!!, fileSize, fileUrl)
        return saved.toDomain(resolveRealms(connectedId))
    }

    private fun resolveRealms(connectedId: Int): List<RealmDynamo> {
        val connectedRealm = connectedRealmRepository.findById(connectedId).orElse(null) ?: return emptyList()
        return connectedRealm.realms.map {
            RealmDynamo(
                id = it.id.toLong(),
                locale = it.locale.name,
                name = it.name,
                slug = it.slug,
                timezone = it.timezone,
            )
        }
    }
}

@Repository
class AuctionHouseUpdateLogRepositoryImpl(
    private val auctionHouseRepository: AuctionHouseRepository,
    private val connectedRealmRepository: ConnectedRealmRepository,
    private val auctionHouseFileLogRepository: AuctionHouseFileLogRepository,
) : AuctionHouseLogRepository {
    override fun findByIdAndMostRecentLastModified(connectedRealmId: Int): List<AuctionHouseUpdateLog> =
        auctionHouseFileLogRepository
            .findByAuctionHouseConnectedIdOrderByLastModifiedDesc(connectedRealmId, PageRequest.of(0, 72))
            .map { it.toDomain() }

    override fun findNewestEntryForConnectedRealm(connectedRealmId: Int): AuctionHouseUpdateLog? =
        auctionHouseFileLogRepository
            .findByAuctionHouseConnectedIdOrderByLastModifiedDesc(connectedRealmId, PageRequest.of(0, 1))
            .firstOrNull()
            ?.toDomain()

    override fun save(
        connectedId: Int,
        lastModified: Instant,
        size: Double,
        url: String,
    ): AuctionHouseUpdateLog {
        val auctionHouse =
            auctionHouseRepository.findByConnectedId(connectedId).orElse(null)
                ?: connectedRealmRepository.findById(connectedId).orElse(null)?.auctionHouse
                ?: throw IllegalStateException(
                    "AuctionHouse with connectedId=$connectedId must exist before writing logs",
                )
        val lastModifiedInstant = lastModified.toJavaInstant()
        val existingLog =
            auctionHouseFileLogRepository.findByAuctionHouseConnectedIdAndLastModified(
                connectedId,
                lastModifiedInstant,
            )

        if (existingLog != null) {
            return existingLog.toDomain()
        }

        val previousLogEntry =
            auctionHouseFileLogRepository
                .findByAuctionHouseConnectedIdOrderByLastModifiedDesc(connectedId, PageRequest.of(0, 1))
                .firstOrNull()
        val timeSincePrevious =
            if (previousLogEntry?.lastModified == null) {
                0L
            } else {
                lastModified.minus(previousLogEntry.lastModified!!.toKotlin()).inWholeMilliseconds
            }

        val saved =
            auctionHouseFileLogRepository.save(
                AuctionHouseFileLog(
                    lastModified = lastModifiedInstant,
                    timeSincePreviousDump = timeSincePrevious,
                    file =
                        FileReference(
                            path = url,
                            bucketName = extractBucketName(url),
                            created = ZonedDateTime.now(),
                            size = size,
                        ),
                    auctionHouse = auctionHouse,
                ),
            )
        return saved.toDomain()
    }

    private fun extractBucketName(url: String): String =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .ifBlank { "unknown" }
}

private fun AuctionHouse.applyDomain(domain: AuctionHouseDomain): AuctionHouse {
    val connectedIdValue = domain.connectedId.takeIf { it != 0 } ?: domain.id ?: 0
    val regionValue =
        domain.region.takeIf { connectedIdValue < 0 || it != Region.Europe || domain.id == null }
            ?: region

    return apply {
        connectedId = connectedIdValue
        region = regionValue
        autoUpdate = domain.autoUpdate
        avgDelay = domain.avgDelay
        gameBuild = domain.gameBuild
        highestDelay = domain.highestDelay
        lastDailyPriceUpdate = domain.lastDailyPriceUpdate?.toJavaInstant()
        lastHistoryDeleteEvent = domain.lastHistoryDeleteEvent?.toJavaInstant()
        lastHistoryDeleteEventDaily = domain.lastHistoryDeleteEventDaily?.toJavaInstant()
        lastModified = domain.lastModified?.toJavaInstant()
        lastRequested = domain.lastRequested?.toJavaInstant()
        lastStatsInsert = domain.lastStatsInsert?.toJavaInstant()
        lastTrendUpdateInitiation = domain.lastTrendUpdateInitiation?.toJavaInstant()
        lowestDelay = domain.lowestDelay
        nextUpdate = domain.nextUpdate?.toJavaInstant()
        statsLastModified = domain.stats.lastModified
        updateAttempts = domain.updateAttempts
        auctionFile = domain.url.toFileReference(domain.size, auctionFile)
        statsFile = domain.stats.url.toFileReference(statsFile?.size ?: 0.0, statsFile)
    }
}

private fun AuctionHouse.toDomain(realms: List<RealmDynamo>): AuctionHouseDomain =
    AuctionHouseDomain(
        id = connectedId,
        region = region,
        autoUpdate = autoUpdate,
        avgDelay = avgDelay,
        connectedId = connectedId,
        gameBuild = gameBuild,
        highestDelay = highestDelay,
        lastDailyPriceUpdate = lastDailyPriceUpdate?.toKotlin(),
        lastHistoryDeleteEvent = lastHistoryDeleteEvent?.toKotlin(),
        lastHistoryDeleteEventDaily = lastHistoryDeleteEventDaily?.toKotlin(),
        lastModified = lastModified?.toKotlin(),
        lastRequested = lastRequested?.toKotlin(),
        lastStatsInsert = lastStatsInsert?.toKotlin(),
        lastTrendUpdateInitiation = lastTrendUpdateInitiation?.toKotlin(),
        lowestDelay = lowestDelay ?: 0L,
        nextUpdate = nextUpdate?.toKotlin(),
        realms = realms,
        size = auctionFile?.size ?: 0.0,
        stats =
            StatsDynamo(
                lastModified = statsLastModified ?: 0L,
                url = statsFile?.path.orEmpty(),
            ),
        updateAttempts = updateAttempts ?: 0,
        url = auctionFile?.path.orEmpty(),
    )

private fun AuctionHouseFileLog.toDomain(): AuctionHouseUpdateLog =
    AuctionHouseUpdateLog(
        id = auctionHouse?.connectedId ?: 0,
        lastModified = requireNotNull(lastModified).toKotlin(),
        size = file.size,
        timeSincePreviousDump = timeSincePreviousDump,
        url = file.path,
    )

private fun String.toFileReference(
    size: Double,
    existing: FileReference?,
): FileReference? {
    if (isBlank()) {
        return existing
    }

    return FileReference(
        id = existing?.id,
        path = this,
        bucketName = substringAfter("://", "").substringBefore('/').ifBlank { existing?.bucketName.orEmpty() },
        created = existing?.created ?: ZonedDateTime.now(),
        size = size,
    )
}
