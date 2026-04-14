package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.repository.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.rds.AuctionHouseFileLogRepository
import org.springframework.stereotype.Service
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
    private val auctionHouseFileLogRepository: AuctionHouseFileLogRepository,
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
        auctionHouse.lowestDelay = auctionHouse.lowestDelay ?: 0L
        auctionHouse.avgDelay = auctionHouse.avgDelay ?: 60L
        auctionHouse.highestDelay = auctionHouse.highestDelay ?: 0L
        auctionHouse.updateAttempts = auctionHouse.updateAttempts ?: 0

        val savedAuctionHouse = auctionHouseEntityRepository.save(auctionHouse)
        if (connectedRealm.auctionHouse.id != savedAuctionHouse.id) {
            connectedRealm.auctionHouse = savedAuctionHouse
        }
    }

    fun updateTimes(
        id: Int,
        newLastModified: Instant?,
        isSuccess: Boolean,
        url: String? = null,
    ) {
        val auctionHouse = repository.findById(id).orElse(null) ?: return

        if (isSuccess && newLastModified != null) {
            requireNotNull(url) { "URL must be provided for successful updates" }

            auctionHouse.lastModified = newLastModified
            auctionHouse.updateAttempts = 0
            auctionHouse.url = url
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
     * Returns the min, avg and max delay summary based on file-log rows from the past 72 hours.
     */
    private fun getUpdateStats(id: Int): Triple<Long, Long, Long> {
        val stats = auctionHouseFileLogRepository.findDelayStatsByConnectedId(id)
        val lowestDelay =
            stats.getMinDelayMinutes()?.coerceAtLeast(MINIMUM_DELAY_FLOOR_MINUTES) ?: DEFAULT_DELAY_MINUTES
        val avgDelay = stats.getAvgDelayMinutes() ?: DEFAULT_DELAY_MINUTES
        val highestDelay =
            stats.getMaxDelayMinutes()?.coerceAtMost(MAXIMUM_DELAY_CEILING_MINUTES) ?: DEFAULT_DELAY_MINUTES

        return Triple(lowestDelay, avgDelay, highestDelay)
    }

    fun findAllByRegion(region: Region) = repository.findAllByRegion(region)

    fun getReadyForUpdate(region: Region) = repository.findReadyForUpdateByRegion(region)
}
