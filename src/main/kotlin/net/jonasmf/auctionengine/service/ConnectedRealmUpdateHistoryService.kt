package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmUpdateHistoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZonedDateTime

@Service
class ConnectedRealmUpdateHistoryService(
    private val repository: ConnectedRealmUpdateHistoryRepository,
) {
    @Transactional
    fun startUpdate(
        connectedRealm: ConnectedRealm,
        auctionCount: Int,
        lastModified: ZonedDateTime,
    ): ConnectedRealmUpdateHistory {
        // Er dt noen hensikt med dette?
        // Også burde compound nøkkelen være annerledes. connected_realm_id + lastModified
        repository.deactivateActive(connectedRealm.id)

        val history =
            ConnectedRealmUpdateHistory(
                auctionCount = auctionCount,
                isActive = true,
                lastModified = lastModified,
                updateTimestamp = ZonedDateTime.now(),
                connectedRealm = connectedRealm,
            )

        val existing = repository.findByConnectedRealmIdAndUpdateTimestamp(connectedRealm.id, lastModified)

        return existing ?: repository.save(history)
    }
}
