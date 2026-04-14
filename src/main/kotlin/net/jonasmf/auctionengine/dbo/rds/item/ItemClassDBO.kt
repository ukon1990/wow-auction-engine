package net.jonasmf.auctionengine.dbo.rds.item

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.jonasmf.auctionengine.dbo.rds.LocaleDBO

@Entity
@Table(name = "item_class")
class ItemClassDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "name_id")
    val name: LocaleDBO,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "item_class_owner_id")
    val itemSubclasses: MutableList<ItemSubclassDBO> = mutableListOf(),
)

@Entity
@Table(
    name = "item_subclass",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_item_subclass_key", columnNames = ["class_id", "subclass_id"]),
    ],
)
class ItemSubclassDBO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val internalId: Long? = null,
    val classId: Int,
    val subclassId: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "display_name_id")
    val displayName: LocaleDBO,
    val hideSubclassInTooltips: Boolean? = null,
)
