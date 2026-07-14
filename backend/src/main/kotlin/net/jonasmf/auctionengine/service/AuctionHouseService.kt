package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.repository.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmUpdateHistoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository as AuctionHouseEntityRepository

@Service
class AuctionHouseService(
    val repository: AuctionHouseRepository,
    private val auctionHouseEntityRepository: AuctionHouseEntityRepository,
    private val connectedRealmUpdateHistoryRepository: ConnectedRealmUpdateHistoryRepository,
) {
    companion object {
        private const val DEFAULT_DELAY_MINUTES = 45L
        private const val MINIMUM_DELAY_FLOOR_MINUTES = 30L
        private const val MAXIMUM_DELAY_CEILING_MINUTES = 120L
    }

    fun createIfMissing(connectedRealm: ConnectedRealm) {
        if (connectedRealm.realms.isEmpty()) return

        val seededAt = Instant.fromEpochSeconds(0L).toJavaInstant()
        val region =
            connectedRealm.realms
                .first()
                .region.type
        val auctionHouse =
            auctionHouseEntityRepository
                .findByConnectedId(connectedRealm.id)
                .orElse(connectedRealm.auctionHouse)

        auctionHouse.connectedId = connectedRealm.id
        auctionHouse.region = region
        auctionHouse.lastModified = auctionHouse.lastModified ?: seededAt
        auctionHouse.nextUpdate = auctionHouse.nextUpdate ?: seededAt
        auctionHouse.lowestDelay = auctionHouse.lowestDelay
        auctionHouse.avgDelay = auctionHouse.avgDelay
        auctionHouse.highestDelay = auctionHouse.highestDelay
        auctionHouse.updateAttempts = auctionHouse.updateAttempts

        val savedAuctionHouse = auctionHouseEntityRepository.save(auctionHouse)
        if (connectedRealm.auctionHouse.id != savedAuctionHouse.id) {
            connectedRealm.auctionHouse = savedAuctionHouse
        }
    }

    fun updateTimes(
        id: Int,
        newLastModified: Instant?,
        isSuccess: Boolean,
    ) {
        val auctionHouse = repository.findById(id) ?: return

        if (isSuccess && newLastModified != null) {
            auctionHouse.lastModified = newLastModified
            auctionHouse.updateAttempts = 0
            repository.save(auctionHouse)

            val (lowestDelay, avgDelay, highestDelay) = getUpdateStats(id)
            auctionHouse.lowestDelay = lowestDelay
            auctionHouse.avgDelay = avgDelay
            auctionHouse.highestDelay = highestDelay
            auctionHouse.nextUpdate = newLastModified.plus(lowestDelay.minutes)
        } else {
            val now = Clock.System.now()
            auctionHouse.updateAttempts += 1
            val retryDelayMinutes = min(auctionHouse.updateAttempts * 2, 30)
            val jitter = (0..1).random().toLong()

            auctionHouse.nextUpdate = now.plus(retryDelayMinutes.minutes + jitter.minutes)
        }

        repository.save(auctionHouse)
    }

    /**
     * Returns the min, avg and max delay summary based on successful snapshot rows from the past 72 hours.
     */
    private fun getUpdateStats(id: Int): Triple<Long, Long, Long> {
        val stats = connectedRealmUpdateHistoryRepository.findDelayStatsByConnectedId(id)
        val lowestDelay =
            stats.getMinDelayMinutes()?.coerceAtLeast(MINIMUM_DELAY_FLOOR_MINUTES) ?: DEFAULT_DELAY_MINUTES
        val avgDelay = stats.getAvgDelayMinutes() ?: DEFAULT_DELAY_MINUTES
        val highestDelay =
            stats.getMaxDelayMinutes()?.coerceAtMost(MAXIMUM_DELAY_CEILING_MINUTES) ?: DEFAULT_DELAY_MINUTES

        return Triple(lowestDelay, avgDelay, highestDelay)
    }

    fun findAllByRegion(region: Region) = repository.findAllByRegion(region)

    @Transactional
    fun updateLastDailyPriceUpdate(
        connectedRealmId: Int,
        lastDailyPriceUpdate: Instant,
    ): Int =
        auctionHouseEntityRepository.updateLastDailyPriceUpdate(
            connectedRealmId,
            lastDailyPriceUpdate.toJavaInstant(),
        )

    @Transactional
    fun updateLastTsmRegionSync(
        connectedRealmId: Int,
        lastTsmRegionSync: Instant,
    ): Int =
        auctionHouseEntityRepository.updateLastTsmRegionSync(
            connectedRealmId,
            lastTsmRegionSync.toJavaInstant(),
        )

    @Transactional
    fun updateLastHistoryDeleted(
        connectedRealmId: Int,
        lastDeletedTime: Instant,
    ) = auctionHouseEntityRepository.updateLastHistoryDeleteEvent(connectedRealmId, lastDeletedTime.toJavaInstant())

    @Transactional
    fun updateLastHistoryDeleted(
        connectedRealmId: Int,
        lastDeletedTime: OffsetDateTime,
    ) = auctionHouseEntityRepository.updateLastHistoryDeleteEvent(connectedRealmId, lastDeletedTime.toInstant())

    @Transactional
    fun updateLastDailyHistoryDeleted(
        connectedRealmId: Int,
        lastDeletedTime: OffsetDateTime,
    ) = auctionHouseEntityRepository.updateLastHistoryDeleteEventDaily(connectedRealmId, lastDeletedTime.toInstant())

    fun getReadyForUpdate(region: Region) = repository.findReadyForUpdateByRegion(region)

    fun getReadyForHourlyStatsCleanup(hourlyTTL: OffsetDateTime) =
        repository.findAllByLastHistoryDeleteEventBefore(hourlyTTL)

    fun getReadyForDailyStatsCleanup(dailyTTL: OffsetDateTime) =
        repository.findAllByLastHistoryDeleteEventDailyBefore(dailyTTL)
}
