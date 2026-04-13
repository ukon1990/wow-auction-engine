package net.jonasmf.auctionengine.dbo.rds.profession

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.jonasmf.auctionengine.dbo.rds.LocaleDBO

@Entity
@Table(name = "modified_crafting_category")
class ModifiedCraftingCategoryDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val name: LocaleDBO,
)

@Entity
@Table(name = "modified_crafting_slot")
class ModifiedCraftingSlotDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val description: LocaleDBO,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "modified_crafting_slot_id")
    val compatibleCategories: MutableList<ModifiedCraftingCategoryDBO> = mutableListOf(),
    val displayOrder: Int? = null,
)

@Entity
@Table(name = "recipe")
class RecipeDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val name: LocaleDBO,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val description: LocaleDBO? = null,
    val mediaUrl: String? = null,
    val rank: Int? = null,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "recipe_id")
    val modifiedCraftingSlots: MutableList<ModifiedCraftingSlotDBO> = mutableListOf(),
)
