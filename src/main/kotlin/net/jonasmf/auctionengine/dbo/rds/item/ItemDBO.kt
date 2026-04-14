package net.jonasmf.auctionengine.dbo.rds.item

import jakarta.persistence.CascadeType
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.jonasmf.auctionengine.dbo.rds.LocaleDBO

@Entity
@Table(name = "item_quality")
class ItemQualityDBO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val internalId: Long? = null,
    val type: String,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val name: LocaleDBO,
)

@Entity
@Table(name = "inventory_type")
class InventoryTypeDBO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val internalId: Long? = null,
    val type: String,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val name: LocaleDBO,
)

@Entity
@Table(name = "item_appearance_ref")
class ItemAppearanceReferenceDBO(
    @Id
    val id: Int,
    val href: String,
)

@Entity
@Table(name = "item_summary")
class ItemSummaryDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val name: LocaleDBO,
    val href: String? = null,
)

@Entity
@Table(name = "`item`")
class ItemDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val name: LocaleDBO,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val quality: ItemQualityDBO,
    val level: Int,
    val requiredLevel: Int,
    val mediaUrl: String,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val itemClass: ItemClassDBO,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val itemSubclass: ItemSubclassDBO,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val inventoryType: InventoryTypeDBO,
    val purchasePrice: Int,
    val sellPrice: Int,
    val maxCount: Int,
    val isEquippable: Boolean,
    val isStackable: Boolean,
    val purchaseQuantity: Int,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinTable(
        name = "item_appearance_refs",
        joinColumns = [JoinColumn(name = "item_id")],
        inverseJoinColumns = [JoinColumn(name = "appearance_ref_id")],
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    val appearances: MutableList<ItemAppearanceReferenceDBO> = mutableListOf(),
)
