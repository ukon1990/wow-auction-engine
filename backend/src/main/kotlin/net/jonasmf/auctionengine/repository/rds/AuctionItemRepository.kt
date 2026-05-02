package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AuctionItemRepository : JpaRepository<AuctionItem, Long> {
    fun findByVariantHash(variantHash: String): AuctionItem?

    @Query("SELECT ai FROM AuctionItem ai WHERE ai.variantHash IN :variantHashes")
    fun findAllByVariantHashes(
        @Param("variantHashes") variantHashes: Collection<String>,
    ): List<AuctionItem>
}
