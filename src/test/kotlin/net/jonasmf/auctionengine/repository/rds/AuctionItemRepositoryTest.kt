package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionItem
import net.jonasmf.auctionengine.utility.AuctionVariantKeyUtility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AuctionItemRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var repository: AuctionItemRepository

    @Test
    fun `should treat different bonus lists as different item variants`() {
        repository.save(
            AuctionItem(
                itemId = 19019,
                variantHash =
                    AuctionVariantKeyUtility.variantHash(
                        itemId = 19019,
                        bonusKey = "12251,12252,12499",
                        modifierKey = "",
                        context = 52,
                        petBreedId = null,
                        petLevel = null,
                        petQualityId = null,
                        petSpeciesId = null,
                    ),
                bonusLists = "12251,12252,12499",
                context = 52,
                petBreedId = null,
                petLevel = null,
                petQualityId = null,
                petSpeciesId = null,
            ),
        )
        repository.save(
            AuctionItem(
                itemId = 19019,
                variantHash =
                    AuctionVariantKeyUtility.variantHash(
                        itemId = 19019,
                        bonusKey = "12251,12253,12499",
                        modifierKey = "",
                        context = 52,
                        petBreedId = null,
                        petLevel = null,
                        petQualityId = null,
                        petSpeciesId = null,
                    ),
                bonusLists = "12251,12253,12499",
                context = 52,
                petBreedId = null,
                petLevel = null,
                petQualityId = null,
                petSpeciesId = null,
            ),
        )

        val first =
            repository.findByVariantHash(
                AuctionVariantKeyUtility.variantHash(
                    itemId = 19019,
                    bonusKey = "12251,12252,12499",
                    modifierKey = "",
                    context = 52,
                    petBreedId = null,
                    petLevel = null,
                    petQualityId = null,
                    petSpeciesId = null,
                ),
            )
        val second =
            repository.findByVariantHash(
                AuctionVariantKeyUtility.variantHash(
                    itemId = 19019,
                    bonusKey = "12251,12253,12499",
                    modifierKey = "",
                    context = 52,
                    petBreedId = null,
                    petLevel = null,
                    petQualityId = null,
                    petSpeciesId = null,
                ),
            )

        assertNotNull(first)
        assertNotNull(second)
        assertNotNull(first!!.id)
        assertNotNull(second!!.id)
        assertEquals("12251,12252,12499", first.bonusLists)
        assertEquals("12251,12253,12499", second.bonusLists)
    }
}
