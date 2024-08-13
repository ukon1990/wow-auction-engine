package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionItem
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AuctionItemRepository: JpaRepository<AuctionItem, Long> {
    
    @Query("SELECT ai FROM AuctionItem ai WHERE ai.itemId = :itemId")
    fun findByItemId(@Param("itemId") itemId: Int): AuctionItem?
    
    @Query("SELECT ai FROM AuctionItem ai WHERE ai.itemId IN :itemIds")
    fun findByItemIds(@Param("itemIds") itemIds: List<Int>): List<AuctionItem>
    
    @Query("""
        SELECT ai FROM AuctionItem ai 
        WHERE ai.itemId = :itemId 
        AND ai.petBreedId = :petBreedId 
        AND ai.petLevel = :petLevel 
        AND ai.petQualityId = :petQualityId 
        AND ai.petSpeciesId = :petSpeciesId 
        AND ai.context = :context
    """)
    fun findByCompositeKey(
        @Param("itemId") itemId: Int,
        @Param("petBreedId") petBreedId: Int?,
        @Param("petLevel") petLevel: Int?,
        @Param("petQualityId") petQualityId: Int?,
        @Param("petSpeciesId") petSpeciesId: Int?,
        @Param("context") context: Int?
    ): AuctionItem?
    
    @Query("""
        SELECT ai FROM AuctionItem ai 
        WHERE ai.itemId = :itemId 
        AND (ai.petBreedId = :petBreedId OR (ai.petBreedId IS NULL AND :petBreedId IS NULL))
        AND (ai.petLevel = :petLevel OR (ai.petLevel IS NULL AND :petLevel IS NULL))
        AND (ai.petQualityId = :petQualityId OR (ai.petQualityId IS NULL AND :petQualityId IS NULL))
        AND (ai.petSpeciesId = :petSpeciesId OR (ai.petSpeciesId IS NULL AND :petSpeciesId IS NULL))
        AND (ai.context = :context OR (ai.context IS NULL AND :context IS NULL))
    """)
    fun findByCompositeKeyWithNullHandlingList(
        @Param("itemId") itemId: Int,
        @Param("petBreedId") petBreedId: Int?,
        @Param("petLevel") petLevel: Int?,
        @Param("petQualityId") petQualityId: Int?,
        @Param("petSpeciesId") petSpeciesId: Int?,
        @Param("context") context: Int?
    ): List<AuctionItem>
}