package net.jonasmf.auctionengine.dto.realm

import net.jonasmf.auctionengine.constant.RealmPopulation
import net.jonasmf.auctionengine.constant.RealmStatus
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.dto.LocaleTypeValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConnectedRealmDTOTest {
    @Test
    fun `should use fetch context region instead of payload region name`() {
        val dto =
            ConnectedRealmDTO(
                id = 205,
                hasQueue = false,
                status = LocaleTypeValue(id = 1, type = RealmStatus.UP, name = null),
                population = LocaleTypeValue(id = 1, type = RealmPopulation.LOW, name = null),
                realms =
                    listOf(
                        RealmDTO(
                            id = 205,
                            region =
                                LocaleTypeValue(
                                    id = 3,
                                    type = Region.Korea,
                                    name = localeText("Korea"),
                                ),
                            name = localeText("Azshara"),
                            category = localeText("Normal"),
                            locale = "koKR",
                            timezone = "UTC",
                            type = LocaleTypeValue(id = null, type = "PVE", name = null),
                            isTournament = false,
                            slug = "azshara",
                        ),
                    ),
                mythicLeaderboards = Href("https://example.invalid/leaderboards"),
                auctions = Href("https://example.invalid/auctions"),
            )

        val connectedRealm = dto.toDBO(Region.Europe)
        val realm = connectedRealm.realms.single()

        assertEquals(Region.Europe, realm.region.type)
        assertEquals(2, realm.region.id)
    }

    private fun localeText(value: String) =
        LocaleDTO(
            en_US = value,
            es_MX = value,
            pt_BR = value,
            pt_PT = value,
            de_DE = value,
            en_GB = value,
            es_ES = value,
            fr_FR = value,
            it_IT = value,
            ru_RU = value,
            ko_KR = value,
            zh_TW = value,
            zh_CN = value,
        )
}
