package net.jonasmf.auctionengine.realm.repository

import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RegionRepository : JpaRepository<RegionDBO, Int>
