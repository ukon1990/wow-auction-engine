package net.jonasmf.auctionengine.dto.item

import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.dto.MediaDTO

data class ItemDetail(
    val key: Href,
    val id: Int
)

data class Binding(
    val type: String,
    val name: LocaleDTO
)

data class Weapon(
    val damage: Damage,
    val attack_speed: AttackSpeed,
    val dps: Dps
)

data class Damage(
    val min_value: Int,
    val max_value: Int,
    val display_string: String,
    val damage_class: DamageClass
)

data class DamageClass(
    val type: String,
    val name: LocaleDTO
)

data class AttackSpeed(
    val value: Int,
    val display_string: LocaleDTO
)

data class Dps(
    val value: Double,
    val display_string: LocaleDTO
)

data class Stat(
    val type: StatType,
    val value: Int,
    val is_negated: Boolean,
    val display: Display
)

data class StatType(
    val type: String,
    val name: LocaleDTO
)

data class Display(
    val display_string: String,
    val color: Color
)

data class Color(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Double
)

data class Spell(
    val spell: SpellDetail,
    val description: String
)

data class SpellDetail(
    val key: Href,
    val name: String,
    val id: Int
)

data class Requirements(
    val level: Level
)

data class Level(
    val value: Int,
    val display_string: LocaleDTO
)

data class Durability(
    val value: Int,
    val display_string: LocaleDTO
)

// Appearance class
data class Appearance(
    val key: Href,
    val id: Int
)

data class ItemPreviewDTO(
    val context: Int,
    val bonus_list: List<Int>,
    val quality: Quality,
    val name: LocaleDTO,
    val media: MediaDTO,
    val item_class: ItemClass,
    val item_subclass: ItemSubclass,
    val inventory_type: InventoryType,
    val binding: Binding,
    val unique_equipped: String,
    val weapon: Weapon,
    val stats: List<Stat>,
    val spells: List<Spell>,
    val requirements: Requirements,
    val level: Level,
    val durability: Durability
)