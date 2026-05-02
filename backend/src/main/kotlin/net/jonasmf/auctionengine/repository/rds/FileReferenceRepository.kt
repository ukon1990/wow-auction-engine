package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.FileReference
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FileReferenceRepository : JpaRepository<FileReference, Long>
