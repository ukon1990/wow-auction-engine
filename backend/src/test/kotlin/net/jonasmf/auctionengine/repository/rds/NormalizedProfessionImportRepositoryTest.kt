package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionData
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperSource
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperSourceFilesInner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class NormalizedProfessionImportRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var repository: NormalizedProfessionImportRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `save binds generated enum values as database scalars`() {
        repository.save(payload(), professionCount = 0, recipeCount = 0)

        val stored =
            jdbcTemplate.queryForMap(
                "SELECT contract_version, addon, character_count FROM normalized_profession_import",
            )

        assertEquals(1, (stored["contract_version"] as Number).toInt())
        assertEquals("AuctionHelper", stored["addon"])
        assertEquals(0, (stored["character_count"] as Number).toInt())
    }

    private fun payload() =
        NormalizedAuctionHelperProfessionData(
            contractVersion = NormalizedAuctionHelperProfessionData.ContractVersion._1,
            source =
                NormalizedAuctionHelperSource(
                    addon = NormalizedAuctionHelperSource.Addon.AUCTION_HELPER,
                    addonVersion = "2.0.0",
                    processorVersion = "test",
                    files =
                        listOf(
                            NormalizedAuctionHelperSourceFilesInner(
                                fileName = "AuctionHelper_Professions.lua",
                                sha256 = "0".repeat(64),
                            ),
                        ),
                ),
            characters = emptyList(),
        )
}
