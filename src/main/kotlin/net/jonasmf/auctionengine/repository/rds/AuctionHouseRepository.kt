package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

@Repository
interface AuctionHouseRepository : JpaRepository<AuctionHouse, Int> {
    fun findByConnectedId(connectedId: Int): Optional<AuctionHouse>

    fun findAllByRegion(region: Region): List<AuctionHouse>

    fun findAllByRegionAndNextUpdateLessThanEqualOrderByNextUpdateAsc(
        region: Region,
        nextUpdate: Instant,
        pageable: Pageable,
    ): List<AuctionHouse>
}
