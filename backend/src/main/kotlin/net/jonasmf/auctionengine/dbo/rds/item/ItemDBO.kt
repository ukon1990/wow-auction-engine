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
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.jonasmf.auctionengine.dbo.rds.LocaleDBO

@Entity
@Table(
    name = "item_quality",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_item_quality_type", columnNames = ["type"]),
    ],
)
class ItemQualityDBO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val internalId: Long? = null,
    val type: String,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "name_id")
    val name: LocaleDBO,
)

@Entity
@Table(
    name = "inventory_type",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_inventory_type_type", columnNames = ["type"]),
    ],
)
class InventoryTypeDBO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val internalId: Long? = null,
    val type: String,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "name_id")
    val name: LocaleDBO,
)

@Entity
@Table(
    name = "item_binding",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_item_binding_type", columnNames = ["type"]),
    ],
)
class ItemBindingDBO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val internalId: Long? = null,
    val type: String,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "name_id")
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
    @JoinColumn(name = "name_id")
    val name: LocaleDBO,
    val href: String? = null,
)

@Entity
@Table(name = "`item`")
class ItemDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "name_id")
    val name: LocaleDBO,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "quality_id")
    val quality: ItemQualityDBO,
    val level: Int,
    val requiredLevel: Int,
    val mediaUrl: String,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_class_id")
    val itemClass: ItemClassDBO,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_subclass_id")
    val itemSubclass: ItemSubclassDBO,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inventory_type_id")
    val inventoryType: InventoryTypeDBO,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "binding_id")
    val binding: ItemBindingDBO? = null,
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
        uniqueConstraints = [
            UniqueConstraint(
                name = "uk_item_appearance_ref_pair",
                columnNames = ["item_id", "appearance_ref_id"],
            ),
        ],
    )
    val appearances: MutableList<ItemAppearanceReferenceDBO> = mutableListOf(),
)
