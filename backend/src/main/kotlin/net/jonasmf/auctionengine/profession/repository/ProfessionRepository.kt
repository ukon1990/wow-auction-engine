package net.jonasmf.auctionengine.profession.repository

import net.jonasmf.auctionengine.dbo.rds.profession.ProfessionDBO
import org.springframework.data.jpa.repository.JpaRepository

interface ProfessionRepository : JpaRepository<ProfessionDBO, Int>
