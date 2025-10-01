package net.jonasmf.auctionengine.dbo.rds

import jakarta.persistence.Entity
import jakarta.persistence.Id

enum class Quality {
    POOR,
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    ARTIFACT,
    HEIRLOOM,
}

@Entity
data class Item(
    @Id
    val id: Long,
    val quality: Quality,
)
