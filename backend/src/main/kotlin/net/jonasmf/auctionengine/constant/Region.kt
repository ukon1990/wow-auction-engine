package net.jonasmf.auctionengine.constant

import net.jonasmf.auctionengine.generated.model.Realm

enum class Region(
    val code: String,
) {
    NorthAmerica("us"),
    Europe("eu"),
    Korea("kr"),
    Taiwan("tw"),
    ;

    companion object {
        fun fromString(value: String): Region {
            val normalized = value.trim()
            return entries.firstOrNull {
                it.name.equals(normalized, ignoreCase = true) ||
                    it.code.equals(normalized, ignoreCase = true)
            } ?: throw IllegalArgumentException("Unknown region value: $value")
        }

        fun toDto(region: Region): Realm.Region =
            when (region) {
                Taiwan -> Realm.Region.TW
                Korea -> Realm.Region.KR
                NorthAmerica -> Realm.Region.US
                else -> Realm.Region.EU
            }
    }
}
