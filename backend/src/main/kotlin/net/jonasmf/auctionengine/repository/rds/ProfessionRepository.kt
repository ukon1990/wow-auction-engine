package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.profession.ProfessionDBO
import org.springframework.data.jpa.repository.JpaRepository

interface ProfessionRepository : JpaRepository<ProfessionDBO, Int>
