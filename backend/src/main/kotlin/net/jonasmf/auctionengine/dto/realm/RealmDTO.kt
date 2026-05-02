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
    val slug: String,
) {
    fun toDBO(regionType: Region): Realm {
        val locale = Locale.fromCompactString(locale)
        return Realm(
            id = id,
            region =
                RegionDBO(
                    regionId(regionType),
                    regionType.name,
                    regionType,
                ),
            name = localeToProperty(locale, name) ?: name.en_GB,
            category = category.en_GB,
            locale = locale,
            timezone = timezone,
            gameBuild = GameBuildVersion.RETAIL,
            slug = slug,
        )
    }

    fun payloadRegion(): Region? =
        when (region?.name?.en_GB) {
            "Europe" -> Region.Europe
            "North America" -> Region.NorthAmerica
            "Korea" -> Region.Korea
            "Taiwan" -> Region.Taiwan
            null -> null
            else -> null
        }

    private fun regionId(regionType: Region): Int =
        when (regionType) {
            Region.NorthAmerica -> 1
            Region.Europe -> 2
            Region.Korea -> 3
            Region.Taiwan -> 4
        }
}
