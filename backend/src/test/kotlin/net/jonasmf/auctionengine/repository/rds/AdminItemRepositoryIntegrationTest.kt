package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class AdminItemRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var repository: AdminItemRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun seedItems() {
        insertItem(id = 224_025, nameId = 91_001, name = "Core Alloy", rank = 2)
        insertItem(id = 224_026, nameId = 91_002, name = "Tempered Core Alloy", rank = 3)
    }

    @Test
    fun `searches localized item names using the real item view`() {
        val result = searchItems("Core Alloy")

        assertThat(result.totalItems).isEqualTo(2)
        assertThat(result.items)
            .extracting<Int> { it.id }
            .containsExactly(224_025, 224_026)
        assertThat(result.items.first().effective.name).isEqualTo("Core Alloy")
        assertThat(result.items.first().effective.rank).isEqualTo(2)
    }

    @Test
    fun `searches an exact numeric item id using the real item view`() {
        val result = searchItems("224025")

        assertThat(result.totalItems).isEqualTo(1)
        assertThat(result.items.single().id).isEqualTo(224_025)
        assertThat(result.items.single().effective.name).isEqualTo("Core Alloy")
        assertThat(result.items.single().effective.rank).isEqualTo(2)
    }

    private fun searchItems(query: String): AdminItemSearchResult =
        repository.searchItems(
            query = query,
            hasBase = null,
            hasOverride = null,
            itemClassId = null,
            itemSubclassId = null,
            expansionId = null,
            hasRecipe = null,
            page = 1,
            pageSize = 20,
            localeColumnSuffix = "en_us",
        )

    private fun insertItem(
        id: Int,
        nameId: Long,
        name: String,
        rank: Int,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO locale (id, en_gb, en_us, source_type, source_key, source_field)
            VALUES (?, ?, ?, 'ITEM', ?, 'name')
            """.trimIndent(),
            nameId,
            name,
            name,
            id.toString(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, rank, max_count, purchase_price,
                purchase_quantity, required_level, sell_price, name_id, is_override
            ) VALUES (?, FALSE, TRUE, 1, ?, 200, 0, 1, 1, 0, ?, FALSE)
            """.trimIndent(),
            id,
            rank,
            nameId,
        )
    }
}
