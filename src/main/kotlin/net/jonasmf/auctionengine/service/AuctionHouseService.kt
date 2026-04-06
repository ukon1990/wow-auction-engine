package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseDynamoRepository
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseUpdateLogDynamoRepository
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Service
class AuctionHouseService(
    val repository: AuctionHouseDynamoRepository,
    val auctionHouseLogRepository: AuctionHouseUpdateLogDynamoRepository,
) {
    fun createIfMissing(connectedRealm: ConnectedRealm) {
        val auctionHouse = repository.findById(connectedRealm.id)
        if (!auctionHouse.isEmpty) return
        if (connectedRealm.realms.isEmpty()) return
        val newAuctionHouse =
            AuctionHouse(
                id = connectedRealm.id,
                region =
                    connectedRealm.realms
                        .first()
                        .region.type,
                realmSlugs = connectedRealm.realms.joinToString(",") { it.slug },
                // Only for new auction houses. We set it way back in the past
                lastModified = Instant.fromEpochSeconds(0L),
            )
        repository.save(newAuctionHouse)
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
