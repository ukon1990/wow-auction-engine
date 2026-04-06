package net.jonasmf.auctionengine.dto.realm

import aws.smithy.kotlin.runtime.util.type
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
    fun toDBO(): Realm {
        val locale = Locale.fromCompactString(locale)
        return Realm(
            id = id,
            region =
                RegionDBO(
                    region?.id,
                    region?.name?.en_GB ?: Region.Europe.name,
                    region?.type ?: Region.Europe,
                ),
            name = localeToProperty(locale, name) ?: name.en_GB,
            category = category.en_GB,
            locale = locale,
            timezone = timezone,
            gameBuild = GameBuildVersion.RETAIL,
            slug = slug,
        )
    }
}
