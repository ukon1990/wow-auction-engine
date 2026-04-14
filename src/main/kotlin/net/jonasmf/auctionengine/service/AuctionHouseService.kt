package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.repository.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.AuctionHouseUpdateLogRepository
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository as AuctionHouseEntityRepository

@Service
class AuctionHouseService(
    val repository: AuctionHouseRepository,
    val auctionHouseLogRepository: AuctionHouseUpdateLogRepository,
    private val auctionHouseEntityRepository: AuctionHouseEntityRepository,
) {
    fun createIfMissing(connectedRealm: ConnectedRealm) {
        if (connectedRealm.realms.isEmpty()) return

        val seededAt = Instant.fromEpochSeconds(0L).toJavaInstant()
        val region =
            connectedRealm.realms
                .first()
                .region.type
        val auctionHouse = connectedRealm.auctionHouse

        auctionHouse.connectedId = connectedRealm.id
        auctionHouse.region = region
        auctionHouse.lastModified = auctionHouse.lastModified ?: seededAt
        auctionHouse.nextUpdate = auctionHouse.nextUpdate ?: seededAt
        auctionHouse.lowestDelay = auctionHouse.lowestDelay ?: 0L
        auctionHouse.avgDelay = auctionHouse.avgDelay ?: 60L
        auctionHouse.highestDelay = auctionHouse.highestDelay ?: 0L
        auctionHouse.updateAttempts = auctionHouse.updateAttempts ?: 0

        auctionHouseEntityRepository.save(auctionHouse)
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

            val previousLastModified = auctionHouse.lastModified
            auctionHouse.lastModified = newLastModified
            auctionHouse.updateAttempts = 0
            val (lowestDelay, avgDelay, highestDelay) = getUpdateStats(id, previousLastModified)
            auctionHouse.lowestDelay = lowestDelay
            auctionHouse.avgDelay = avgDelay
            auctionHouse.highestDelay = highestDelay
            auctionHouse.url = url
            val nextUpdateTime = max(auctionHouse.lowestDelay, 30).minutes
            auctionHouse.nextUpdate = newLastModified.plus(nextUpdateTime)
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
     * Returns the min, max and avg(in that order),
     * based on the update history logs for the given realm
     */
    private fun getUpdateStats(
        id: Int,
        lastModified: Instant?,
    ): Triple<Long, Long, Long> {
        val updateLogs =
            auctionHouseLogRepository
                .findByIdAndMostRecentLastModified(id)
                .sortedByDescending { it.lastModified }
        var min = 0L
        var avg = 0L
        var max = 0L

        for ((index, log) in updateLogs.withIndex()) {
            val previousTime =
                if (index == 0) {
                    lastModified
                } else {
                    updateLogs[index - 1].lastModified
                }
            if (previousTime == null) continue

            val currentTime = updateLogs[index].lastModified
            val difference = previousTime.minus(currentTime).inWholeMinutes

            if (min == 0L || difference < min) min = difference
            if (difference > max) max = difference
            avg =
                if (avg == 0L) {
                    difference
                } else {
                    (difference + avg) / 2
                }
        }

        return Triple(min, avg, max)
    }

    fun findAllByRegion(region: Region) = repository.findAllByRegion(region)

    fun getReadyForUpdate(region: Region) = repository.findReadyForUpdateByRegion(region)
}
