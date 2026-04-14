package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dbo.rds.item.InventoryTypeDBO
import net.jonasmf.auctionengine.dbo.rds.item.ItemAppearanceDBO
import net.jonasmf.auctionengine.dbo.rds.item.ItemAppearanceReferenceDBO
import net.jonasmf.auctionengine.dbo.rds.item.ItemClassDBO
import net.jonasmf.auctionengine.dbo.rds.item.ItemDBO
import net.jonasmf.auctionengine.dbo.rds.item.ItemQualityDBO
import net.jonasmf.auctionengine.dbo.rds.item.ItemSubclassDBO
import net.jonasmf.auctionengine.dbo.rds.item.ItemSummaryDBO
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.domain.item.InventoryType
import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.domain.item.ItemAppearance
import net.jonasmf.auctionengine.domain.item.ItemAppearanceReference
import net.jonasmf.auctionengine.domain.item.ItemClass
import net.jonasmf.auctionengine.domain.item.ItemQuality
import net.jonasmf.auctionengine.domain.item.ItemSubclass
import net.jonasmf.auctionengine.domain.item.ItemSummary
import net.jonasmf.auctionengine.dto.ReferenceDTO
import net.jonasmf.auctionengine.dto.item.InventoryTypeDTO
import net.jonasmf.auctionengine.dto.item.ItemAppearanceReferenceDTO
import net.jonasmf.auctionengine.dto.item.ItemClassReferenceDTO
import net.jonasmf.auctionengine.dto.item.ItemDTO
import net.jonasmf.auctionengine.dto.item.ItemQualityDTO
import net.jonasmf.auctionengine.dto.item.ItemSubclassReferenceDTO
import net.jonasmf.auctionengine.dto.itemappearance.ItemAppearanceDTO
import net.jonasmf.auctionengine.dto.itemclass.ItemClassDTO
import net.jonasmf.auctionengine.dto.itemclass.ItemSubclassDTO

fun ItemQualityDTO.toDomain() =
    ItemQuality(
        type = type,
        name = name,
    )

fun InventoryTypeDTO.toDomain() =
    InventoryType(
        type = type,
        name = name,
    )

fun ItemClassReferenceDTO.toDomain(itemSubclasses: List<ItemSubclass> = emptyList()) =
    ItemClass(
        id = id,
        name = name,
        itemSubclasses = itemSubclasses,
    )

fun ItemSubclassReferenceDTO.toDomain(classId: Int) =
    ItemSubclass(
        classId = classId,
        subclassId = id,
        displayName = name,
        hideSubclassInTooltips = null,
    )

fun ReferenceDTO.toItemSubclassDomain(classId: Int) =
    ItemSubclass(
        classId = classId,
        subclassId = id,
        displayName = name,
        hideSubclassInTooltips = null,
    )

fun ItemAppearanceReferenceDTO.toDomain() =
    ItemAppearanceReference(
        id = id,
        href = key.href,
    )

fun ReferenceDTO.toItemSummaryDomain() =
    ItemSummary(
        id = id,
        name = name,
        href = key.href,
    )

fun ItemDTO.toDomain() =
    Item(
        id = id,
        name = name,
        quality = quality.toDomain(),
        level = level,
        requiredLevel = requiredLevel,
        mediaUrl = media.key.href,
        itemClass = itemClass.toDomain(),
        itemSubclass = itemSubclass.toDomain(itemClass.id),
        inventoryType = inventoryType.toDomain(),
        purchasePrice = purchasePrice,
        sellPrice = sellPrice,
        maxCount = maxCount,
        isEquippable = isEquippable,
        isStackable = isStackable,
        purchaseQuantity = purchaseQuantity,
        appearances = appearances.map { it.toDomain() },
    )

fun ItemClassDTO.toDomain() =
    ItemClass(
        id = classId,
        name = name,
        itemSubclasses = itemSubclasses.map { it.toItemSubclassDomain(classId) },
    )

fun ItemSubclassDTO.toDomain() =
    ItemSubclass(
        classId = classId,
        subclassId = subclassId,
        displayName = displayName,
        hideSubclassInTooltips = hideSubclassInTooltips,
    )

fun ItemAppearanceDTO.toDomain() =
    ItemAppearance(
        id = id,
        slot = slot.toDomain(),
        itemClass = itemClass.toDomain(),
        itemSubclass = itemSubclass.toDomain(itemClass.id),
        itemDisplayInfoId = itemDisplayInfoId,
        items = items.map { it.toItemSummaryDomain() },
        mediaUrl = media.key.href,
    )

fun ItemQuality.toDBO() =
    ItemQualityDBO(
        type = type,
        name = name.toDBO(LocaleSourceType.ITEM_QUALITY, localeSourceKey(type), "name"),
    )

fun ItemQualityDBO.toDomain() =
    ItemQuality(
        type = type,
        name = name.toDTO(),
    )

fun InventoryType.toDBO() =
    InventoryTypeDBO(
        type = type,
        name = name.toDBO(LocaleSourceType.INVENTORY_TYPE, localeSourceKey(type), "name"),
    )

fun InventoryTypeDBO.toDomain() =
    InventoryType(
        type = type,
        name = name.toDTO(),
    )

fun ItemAppearanceReference.toDBO() =
    ItemAppearanceReferenceDBO(
        id = id,
        href = href,
    )

fun ItemAppearanceReferenceDBO.toDomain() =
    ItemAppearanceReference(
        id = id,
        href = href,
    )

fun ItemSummary.toDBO() =
    ItemSummaryDBO(
        id = id,
        name = name.toDBO(LocaleSourceType.ITEM_SUMMARY, localeSourceKey(id), "name"),
        href = href,
    )

fun ItemSummaryDBO.toDomain() =
    ItemSummary(
        id = id,
        name = name.toDTO(),
        href = href,
    )

fun ItemClass.toDBO() =
    ItemClassDBO(
        id = id,
        name = name.toDBO(LocaleSourceType.ITEM_CLASS, localeSourceKey(id), "name"),
        itemSubclasses = itemSubclasses.map { it.toDBO() }.toMutableList(),
    )

fun ItemClassDBO.toDomain() =
    ItemClass(
        id = id,
        name = name.toDTO(),
        itemSubclasses = itemSubclasses.map { it.toDomain() },
    )

fun ItemSubclass.toDBO() =
    ItemSubclassDBO(
        classId = classId,
        subclassId = subclassId,
        displayName =
            displayName.toDBO(
                LocaleSourceType.ITEM_SUBCLASS,
                localeSourceKey(classId, subclassId),
                "display_name",
            ),
        hideSubclassInTooltips = hideSubclassInTooltips,
    )

fun ItemSubclassDBO.toDomain() =
    ItemSubclass(
        classId = classId,
        subclassId = subclassId,
        displayName = displayName.toDTO(),
        hideSubclassInTooltips = hideSubclassInTooltips,
    )

fun Item.toDBO() =
    ItemDBO(
        id = id,
        name = name.toDBO(LocaleSourceType.ITEM, localeSourceKey(id), "name"),
        quality = quality.toDBO(),
        level = level,
        requiredLevel = requiredLevel,
        mediaUrl = mediaUrl,
        itemClass = itemClass.toDBO(),
        itemSubclass = itemSubclass.toDBO(),
        inventoryType = inventoryType.toDBO(),
        purchasePrice = purchasePrice,
        sellPrice = sellPrice,
        maxCount = maxCount,
        isEquippable = isEquippable,
        isStackable = isStackable,
        purchaseQuantity = purchaseQuantity,
        appearances = appearances.map { it.toDBO() }.toMutableList(),
    )

fun ItemDBO.toDomain() =
    Item(
        id = id,
        name = name.toDTO(),
        quality = quality.toDomain(),
        level = level,
        requiredLevel = requiredLevel,
        mediaUrl = mediaUrl,
        itemClass = itemClass.toDomain(),
        itemSubclass = itemSubclass.toDomain(),
        inventoryType = inventoryType.toDomain(),
        purchasePrice = purchasePrice,
        sellPrice = sellPrice,
        maxCount = maxCount,
        isEquippable = isEquippable,
        isStackable = isStackable,
        purchaseQuantity = purchaseQuantity,
        appearances = appearances.map { it.toDomain() },
    )

fun ItemAppearance.toDBO() =
    ItemAppearanceDBO(
        id = id,
        slot = slot.toDBO(),
        itemClass = itemClass.toDBO(),
        itemSubclass = itemSubclass.toDBO(),
        itemDisplayInfoId = itemDisplayInfoId,
        items = items.map { it.toDBO() }.toMutableList(),
        mediaUrl = mediaUrl,
    )

fun ItemAppearanceDBO.toDomain() =
    ItemAppearance(
        id = id,
        slot = slot.toDomain(),
        itemClass = itemClass.toDomain(),
        itemSubclass = itemSubclass.toDomain(),
        itemDisplayInfoId = itemDisplayInfoId,
        items = items.map { it.toDomain() },
        mediaUrl = mediaUrl,
    )
