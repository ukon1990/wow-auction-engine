package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.profession.ModifiedCraftingCategoryMetadataDBO
import net.jonasmf.auctionengine.dbo.rds.profession.ModifiedCraftingSlotMetadataDBO
import net.jonasmf.auctionengine.dbo.rds.profession.RecipeDBO
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ModifiedCraftingCategoryMetadataRepository : JpaRepository<ModifiedCraftingCategoryMetadataDBO, Int>

interface ModifiedCraftingSlotMetadataRepository : JpaRepository<ModifiedCraftingSlotMetadataDBO, Int>

interface RecipeRepository : JpaRepository<RecipeDBO, Int> {
    @Query("select distinct r.craftedItemId from RecipeDBO r where r.craftedItemId is not null")
    fun findDistinctCraftedItemIds(): List<Int>

    @Query("select distinct reagent.itemId from RecipeDBO r join r.reagents reagent")
    fun findDistinctReagentItemIds(): List<Int>

    fun findDistinctReferencedItemIds(): List<Int> =
        (findDistinctCraftedItemIds() + findDistinctReagentItemIds()).distinct().sorted()
}
