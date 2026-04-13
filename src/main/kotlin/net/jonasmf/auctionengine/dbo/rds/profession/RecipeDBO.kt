package net.jonasmf.auctionengine.dbo.rds.profession

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import net.jonasmf.auctionengine.dbo.rds.LocaleDBO

@Entity
class ModifiedCraftingSlotCategory(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL])
    val name: LocaleDBO,
)

@Entity
class ModifiedCraftingSlot( // reagent-slot-type
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL])
    val description: LocaleDBO,
    @OneToMany(cascade = [CascadeType.ALL])
    val compatibleCategories: MutableList<ModifiedCraftingSlotCategory>,
)

@Entity
class RecipeDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL])
    val name: LocaleDBO,
    val mediaUrl: String,

)
