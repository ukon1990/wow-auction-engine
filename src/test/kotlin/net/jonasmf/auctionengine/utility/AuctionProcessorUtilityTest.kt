package net.jonasmf.auctionengine.utility

import net.jonasmf.auctionengine.dbo.rds.auction.DailyAuctionStats
import net.jonasmf.auctionengine.dbo.rds.auction.HourlyAuctionStats
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionItemDTO
import net.jonasmf.auctionengine.repository.rds.DailyAuctionStatsRepository
import net.jonasmf.auctionengine.repository.rds.HourlyAuctionStatsRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals

class AuctionProcessorUtilityTest {
    private lateinit var dailyRepoCapture: SaveAllCapture<DailyAuctionStats>
    private lateinit var hourlyRepoCapture: SaveAllCapture<HourlyAuctionStats>
    private lateinit var utility: AuctionProcessorUtility

    @BeforeEach
    fun setup() {
        dailyRepoCapture = SaveAllCapture()
        hourlyRepoCapture = SaveAllCapture()
        utility =
            AuctionProcessorUtility(
                dailyStatsRepo =
                    createRepositoryProxy<DailyAuctionStatsRepository, DailyAuctionStats>(dailyRepoCapture),
                hourlyStatsRepo =
                    createRepositoryProxy<HourlyAuctionStatsRepository, HourlyAuctionStats>(hourlyRepoCapture),
            )
    }

    @Test
    fun `processAuctions with empty list does not save stats`() {
        utility.processAuctions(emptyList(), System.currentTimeMillis(), 1, 1)

        assertEquals(1, hourlyRepoCapture.calls.size)
        assertEquals(0, hourlyRepoCapture.calls.single().size)
        assertEquals(1, dailyRepoCapture.calls.size)
        assertEquals(0, dailyRepoCapture.calls.single().size)
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

        utility.processAuctions(listOf(auction), System.currentTimeMillis(), 2, 3)

        assertEquals(1, hourlyRepoCapture.calls.size)
        assertEquals(1, hourlyRepoCapture.calls.single().size)
        assertEquals(1, dailyRepoCapture.calls.size)
        assertEquals(1, dailyRepoCapture.calls.single().size)
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

        assertEquals(1, hourlyRepoCapture.calls.size)
        assertEquals(10, hourlyRepoCapture.calls.single().size)
        assertEquals(1, dailyRepoCapture.calls.size)
        assertEquals(10, dailyRepoCapture.calls.single().size)
    }

    private data class SaveAllCapture<T>(
        val calls: MutableList<List<T>> = mutableListOf(),
    )

    private inline fun <reified TRepo : Any, TEntity> createRepositoryProxy(capture: SaveAllCapture<TEntity>): TRepo =
        Proxy
            .newProxyInstance(
                TRepo::class.java.classLoader,
                arrayOf(TRepo::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "saveAll" -> {
                        @Suppress("UNCHECKED_CAST")
                        val entities = ((args?.firstOrNull() as? Iterable<TEntity>) ?: emptyList()).toList()
                        capture.calls.add(entities)
                        entities
                    }
                    "toString" -> "${TRepo::class.java.simpleName}Proxy"
                    "hashCode" -> System.identityHashCode(capture)
                    "equals" -> false
                    else -> defaultValue(method.returnType)
                }
            }.let { it as TRepo }

    private fun defaultValue(returnType: Class<*>): Any? =
        when {
            !returnType.isPrimitive -> null
            returnType == Boolean::class.javaPrimitiveType -> false
            returnType == Char::class.javaPrimitiveType -> '\u0000'
            returnType == Byte::class.javaPrimitiveType -> 0.toByte()
            returnType == Short::class.javaPrimitiveType -> 0.toShort()
            returnType == Int::class.javaPrimitiveType -> 0
            returnType == Long::class.javaPrimitiveType -> 0L
            returnType == Float::class.javaPrimitiveType -> 0f
            returnType == Double::class.javaPrimitiveType -> 0.0
            else -> null
        }
}
