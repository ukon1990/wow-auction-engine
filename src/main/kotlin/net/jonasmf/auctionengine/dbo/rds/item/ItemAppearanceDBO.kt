package net.jonasmf.auctionengine.dbo.rds.item

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "item_appearance")
class ItemAppearanceDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val slot: InventoryTypeDBO,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val itemClass: ItemClassDBO,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val itemSubclass: ItemSubclassDBO,
    val itemDisplayInfoId: Int,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "item_appearance_id")
    val items: MutableList<ItemSummaryDBO> = mutableListOf(),
    val mediaUrl: String,
)
