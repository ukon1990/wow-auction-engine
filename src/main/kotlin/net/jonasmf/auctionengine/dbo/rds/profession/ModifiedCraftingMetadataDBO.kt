package net.jonasmf.auctionengine.dbo.rds.profession

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.jonasmf.auctionengine.dbo.rds.LocaleDBO

@Entity
@Table(name = "modified_crafting_category_metadata")
class ModifiedCraftingCategoryMetadataDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val name: LocaleDBO,
)

@Entity
@Table(name = "modified_crafting_slot_metadata")
class ModifiedCraftingSlotMetadataDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val description: LocaleDBO,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "modified_crafting_slot_metadata_category", joinColumns = [JoinColumn(name = "slot_id")])
    @Column(name = "category_id")
    val compatibleCategoryIds: MutableSet<Int> = mutableSetOf(),
)
