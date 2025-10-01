package net.jonasmf.auctionengine.dbo.rds

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import java.time.ZonedDateTime

@Entity
data class FileReference(
    @Id
    @GeneratedValue
    val id: Long,
    val path: String,
    val bucketName: String,
    val created: ZonedDateTime,
)
