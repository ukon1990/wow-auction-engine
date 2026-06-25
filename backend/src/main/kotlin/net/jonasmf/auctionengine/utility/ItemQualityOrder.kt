package net.jonasmf.auctionengine.utility

object ItemQualityOrder {
    val TYPES: List<String> =
        listOf(
            "POOR",
            "COMMON",
            "UNCOMMON",
            "RARE",
            "EPIC",
            "LEGENDARY",
            "ARTIFACT",
            "HEIRLOOM",
            "WOW_TOKEN",
        )

    fun rank(type: String?): Int {
        if (type == null) return Int.MAX_VALUE
        val index = TYPES.indexOf(type.uppercase())
        return if (index < 0) Int.MAX_VALUE - 1 else index
    }

    fun sqlOrderByCase(column: String): String =
        buildString {
            append("CASE UPPER($column) ")
            TYPES.forEachIndexed { index, qualityType ->
                append("WHEN '$qualityType' THEN ${index + 1} ")
            }
            append("ELSE ${TYPES.size + 1} END")
        }
}
