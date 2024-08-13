package net.jonasmf.auctionengine.dto.realm

import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.dto.LocaleTypeValue
import net.jonasmf.auctionengine.dto.localeToProperty

data class RealmDTO(
    val id: Int,
    val region: LocaleTypeValue<Region>? = null,
    val name: LocaleDTO,
    val category: LocaleDTO,
    val locale: String,
    val timezone: String,
    val type: LocaleTypeValue<String>,
    @JsonProperty("is_tournament")
    val isTournament: Boolean,
    val slug: String
) {
    fun toDBO(): Realm {
        val locale = Locale.fromCompactString(locale)
        return Realm(
            id = id,
            region = RegionDBO(region?.id, region?.name?.en_GB!!),
            name = name?.let { localeToProperty(locale, it!!) }!!, // Safely access the localized name
            category = category!!.en_GB,
            locale = locale, // Convert Locale enum to its string value
            timezone = timezone,
            gameBuild = GameBuildVersion.RETAIL,
            slug = slug
        )
    }
}
