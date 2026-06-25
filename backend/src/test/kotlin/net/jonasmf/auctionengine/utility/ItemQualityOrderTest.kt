package net.jonasmf.auctionengine.utility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemQualityOrderTest {
    @Test
    fun `ranks qualities in Blizzard tier order`() {
        assertEquals(0, ItemQualityOrder.rank("POOR"))
        assertEquals(1, ItemQualityOrder.rank("COMMON"))
        assertEquals(2, ItemQualityOrder.rank("UNCOMMON"))
        assertEquals(3, ItemQualityOrder.rank("RARE"))
        assertEquals(4, ItemQualityOrder.rank("EPIC"))
        assertEquals(5, ItemQualityOrder.rank("LEGENDARY"))
        assertEquals(6, ItemQualityOrder.rank("ARTIFACT"))
    }

    @Test
    fun `sql order case starts with poor and ends with unknowns`() {
        val sql = ItemQualityOrder.sqlOrderByCase("iq.type")
        assert(sql.startsWith("CASE UPPER(iq.type) WHEN 'POOR' THEN 1 "))
        assert(sql.contains("WHEN 'ARTIFACT' THEN 7"))
        assert(sql.endsWith("ELSE 10 END"))
    }
}
