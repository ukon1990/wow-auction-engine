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
        fun fromString(code: String): Region =
            entries.first { it.name.replace(" ", "").equals(code, ignoreCase = true) }
    }
}
