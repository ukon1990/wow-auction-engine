package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouseFileLog
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface AuctionHouseFileLogRepository : JpaRepository<AuctionHouseFileLog, Long> {
    fun findByAuctionHouseConnectedIdOrderByLastModifiedDesc(
        connectedId: Int,
        pageable: Pageable,
    ): List<AuctionHouseFileLog>

    fun findByAuctionHouseConnectedIdAndLastModified(
        connectedId: Int,
        lastModified: Instant,
    ): AuctionHouseFileLog?
}
