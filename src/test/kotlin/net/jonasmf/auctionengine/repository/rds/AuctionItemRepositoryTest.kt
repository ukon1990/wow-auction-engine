package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionItem
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
                bonusLists = "12251,12253,12499",
                context = 52,
                petBreedId = null,
                petLevel = null,
                petQualityId = null,
                petSpeciesId = null,
            ),
        )

        val first =
            repository.findByCompositeKeyWithNullHandlingList(
                itemId = 19019,
                petBreedId = null,
                petLevel = null,
                petQualityId = null,
                petSpeciesId = null,
                context = 52,
                bonusLists = "12251,12252,12499",
            )
        val second =
            repository.findByCompositeKeyWithNullHandlingList(
                itemId = 19019,
                petBreedId = null,
                petLevel = null,
                petQualityId = null,
                petSpeciesId = null,
                context = 52,
                bonusLists = "12251,12253,12499",
            )

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertNotNull(first.single().id)
        assertNotNull(second.single().id)
        assertEquals("12251,12252,12499", first.single().bonusLists)
        assertEquals("12251,12253,12499", second.single().bonusLists)
    }
}
