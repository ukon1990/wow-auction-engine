package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseDynamoRepository
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Service
class AuctionHouseService(
    val repository: AuctionHouseDynamoRepository,
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
            )
        repository.save(newAuctionHouse)
    }

    fun updateTimes(
        id: Int,
        newLastModified: Instant?,
        isSuccess: Boolean,
    ) {
        val auctionHouse = repository.findById(id).orElse(null) ?: return
        val now = Clock.System.now()

        if (isSuccess && newLastModified != null) {
            val previousLastModified = auctionHouse.lastModified
            auctionHouse.lastModified = newLastModified
            auctionHouse.updateAttempts = 0
            if (previousLastModified != null) {
                val delayMillis = newLastModified.toEpochMilliseconds() - previousLastModified.toEpochMilliseconds()
                if (delayMillis > 0) {
                    auctionHouse.avgDelay = max(1L, delayMillis / 60_000L)
                }
            }
            auctionHouse.nextUpdate = now.plus(auctionHouse.avgDelay.minutes)
        } else {
            auctionHouse.updateAttempts += 1
            val retryDelayMinutes = max(1L, auctionHouse.avgDelay) * auctionHouse.updateAttempts
            auctionHouse.nextUpdate = now.plus(retryDelayMinutes.minutes)
        }

        repository.save(auctionHouse)
    }

    fun findAllByRegion(region: Region) = repository.findAllByRegion(region)

    fun getReadyForUpdate(region: Region) = repository.findReadyForUpdateByRegion(region)
}
