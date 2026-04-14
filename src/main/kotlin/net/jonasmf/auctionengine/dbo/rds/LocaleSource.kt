package net.jonasmf.auctionengine.dbo.rds

object LocaleSourceType {
    const val PROFESSION = "profession"
    const val SKILL_TIER = "skill_tier"
    const val PROFESSION_CATEGORY = "profession_category"
    const val RECIPE = "recipe"
    const val RECIPE_REAGENT = "recipe_reagent"
    const val MODIFIED_CRAFTING_SLOT = "modified_crafting_slot"
    const val MODIFIED_CRAFTING_CATEGORY = "modified_crafting_category"
    const val MODIFIED_CRAFTING_CATEGORY_METADATA = "modified_crafting_category_metadata"
    const val MODIFIED_CRAFTING_SLOT_METADATA = "modified_crafting_slot_metadata"
    const val ITEM = "item"
    const val ITEM_SUMMARY = "item_summary"
    const val ITEM_CLASS = "item_class"
    const val ITEM_SUBCLASS = "item_subclass"
    const val ITEM_QUALITY = "item_quality"
    const val INVENTORY_TYPE = "inventory_type"
    const val ITEM_BINDING = "item_binding"
}

fun localeSourceKey(vararg parts: Any): String = parts.joinToString(":") { it.toString() }
