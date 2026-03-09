package net.jonasmf.auctionengine.utility

import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.dbo.rds.auction.DailyAuctionStats
import net.jonasmf.auctionengine.dbo.rds.auction.HourlyAuctionStats
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionItemDTO
import net.jonasmf.auctionengine.repository.rds.DailyAuctionStatsRepository
import net.jonasmf.auctionengine.repository.rds.HourlyAuctionStatsRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuctionProcessorUtilityTest {
    private lateinit var dailyRepo: DailyAuctionStatsRepository
    private lateinit var hourlyRepo: HourlyAuctionStatsRepository
    private lateinit var utility: AuctionProcessorUtility

    @BeforeEach
    fun setup() {
        dailyRepo = mockk(relaxed = true)
        hourlyRepo = mockk(relaxed = true)
        utility = AuctionProcessorUtility(dailyRepo, hourlyRepo)
    }

    @Test
    fun `processAuctions with empty list does not save stats`() {
        utility.processAuctions(emptyList(), System.currentTimeMillis(), 1, 1)
        verify(exactly = 1) { hourlyRepo.saveAll(emptyList()) }
        verify(exactly = 1) { dailyRepo.saveAll(emptyList()) }
    }

    @Test
    fun `processAuctions with single auction saves stats`() {
        val auction =
            AuctionDTO(
                id = 1L,
                item =
                    AuctionItemDTO(
                        id = 100,
                        modifiers = null,
                        context = null,
                        pet_breed_id = null,
                        pet_level = null,
                        pet_quality_id = null,
                        pet_species_id = null,
                    ),
                quantity = 5,
                unit_price = 12345L,
                buyout = 12345L,
                time_left = net.jonasmf.auctionengine.constant.AuctionTimeLeft.LONG,
            )
        val auctions = listOf(auction)

        utility.processAuctions(auctions, System.currentTimeMillis(), 2, 3)

        verify { hourlyRepo.saveAll(any<Iterable<HourlyAuctionStats>>()) }
        verify { dailyRepo.saveAll(any<Iterable<DailyAuctionStats>>()) }
    }

    @Test
    fun `processAuctions with multiple auctions saves correct stats count`() {
        val auctions =
            (1..10).map {
                AuctionDTO(
                    id = it.toLong(),
                    item =
                        AuctionItemDTO(
                            id = 100 + it,
                            modifiers = null,
                            context = null,
                            pet_breed_id = null,
                            pet_level = null,
                            pet_quality_id = null,
                            pet_species_id = null,
                        ),
                    quantity = it * 2L,
                    unit_price = it * 1000L,
                    buyout = it * 1000L,
                    time_left = net.jonasmf.auctionengine.constant.AuctionTimeLeft.LONG,
                )
            }

        utility.processAuctions(auctions, System.currentTimeMillis(), 5, 7)

        verify { hourlyRepo.saveAll(any<Iterable<HourlyAuctionStats>>()) }
        verify { dailyRepo.saveAll(any<Iterable<DailyAuctionStats>>()) }
    }
}
