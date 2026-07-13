package net.jonasmf.auctionengine.constant

enum class NameSpace(
    val value: String,
) {
    DYNAMIC_CLASSIC("dynamic-classic"),
    DYNAMIC_RETAIL("dynamic-retail"),
    DYNAMIC_US("dynamic-us"),
    DYNAMIC_EU("dynamic-eu"),
    DYNAMIC_KR("dynamic-kr"),
    DYNAMIC_TW("dynamic-tw"),
    STATIC_US("static-us"),
    STATIC_EU("static-eu"),
    STATIC_KR("static-kr"),
    STATIC_TW("static-tw"),
    PROFILE_US("profile-us"),
    PROFILE_EU("profile-eu"),
    PROFILE_KR("profile-kr"),
    PROFILE_TW("profile-tw"),
    ;

    companion object {
        fun getDynamicForRegion(region: Region): NameSpace =
            when (region) {
                Region.NorthAmerica -> DYNAMIC_US
                Region.Europe -> DYNAMIC_EU
                Region.Korea -> DYNAMIC_KR
                Region.Taiwan -> DYNAMIC_TW
            }

        fun getStaticForRegion(region: Region): NameSpace =
            when (region) {
                Region.NorthAmerica -> STATIC_US
                Region.Europe -> STATIC_EU
                Region.Korea -> STATIC_KR
                Region.Taiwan -> STATIC_TW
            }

        fun getProfileForRegion(region: Region): NameSpace =
            when (region) {
                Region.NorthAmerica -> PROFILE_US
                Region.Europe -> PROFILE_EU
                Region.Korea -> PROFILE_KR
                Region.Taiwan -> PROFILE_TW
            }
    }
}
