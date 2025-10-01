import net.jonasmf.auctionengine.constant.Region

enum class NameSpace(
    val value: String,
) {
    DYNAMIC_CLASSIC("dynamic-classic"),
    DYNAMIC_RETAIL("dynamic-retail"),
    DYNAMIC_US("dynamic-us"),
    DYNAMIC_EU("dynamic-eu"),
    DYNAMIC_KR("dynamic-kr"),
    DYNAMIC_TW("dynamic-tw"),
    ;

    companion object {
        fun getDynamicForRegion(region: Region): NameSpace =
            when (region) {
                Region.NorthAmerica -> DYNAMIC_US
                Region.Europe -> DYNAMIC_EU
                Region.Korea -> DYNAMIC_KR
                Region.Taiwan -> DYNAMIC_TW
                else -> throw IllegalArgumentException("Unknown namespace: $region")
            }
    }
}
