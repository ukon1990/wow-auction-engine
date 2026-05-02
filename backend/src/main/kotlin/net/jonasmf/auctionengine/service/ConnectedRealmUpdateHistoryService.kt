package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmUpdateHistoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
        val history =
            ConnectedRealmUpdateHistory(
                auctionCount = auctionCount,
                lastModified = lastModified.toOffsetDateTime(),
                updateTimestamp = ZonedDateTime.now().toOffsetDateTime(),
                connectedRealm = connectedRealm,
            )

        val existing =
            repository.findByConnectedRealmIdAndUpdateTimestamp(
                connectedRealm.id,
                lastModified.toOffsetDateTime(),
            )

        return existing ?: repository.save(history)
    }

    @Transactional
    fun setUpdateToCompleted(
        connectedRealmId: Int,
        lastModified: ZonedDateTime,
    ): Boolean {
        val updatedRows =
            repository.updateCompletedTimeForConnectedRealmAndLastModified(
                connectedRealmId,
                lastModified.toOffsetDateTime(),
                ZonedDateTime.now().toOffsetDateTime(),
            )
        return updatedRows > 0
    }
}
