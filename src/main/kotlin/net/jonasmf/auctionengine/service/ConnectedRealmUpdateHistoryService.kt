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
        repository.deactivateActive(connectedRealm.id)

        val history = ConnectedRealmUpdateHistory(
            auctionCount = auctionCount,
            isActive = true,
            lastModified = lastModified.toLocalDateTime(),
            updateTimestamp = LocalDateTime.now(),
            connectedRealm = connectedRealm,
        )

        return repository.save(history)
    }
}
