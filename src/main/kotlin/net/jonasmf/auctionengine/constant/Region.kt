package net.jonasmf.auctionengine.constant

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
    }
}
