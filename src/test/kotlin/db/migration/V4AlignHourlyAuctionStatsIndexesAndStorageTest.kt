package db.migration

import net.jonasmf.auctionengine.testsupport.container.SharedTestContainers
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

class V4AlignHourlyAuctionStatsIndexesAndStorageTest {
    @Test
    fun `should upgrade existing hourly auction stats table in place`() {
        SharedTestContainers.startMariaDb()

        databaseConnection().use { connection ->
            try {
                execute(connection, "DROP VIEW IF EXISTS v_auction_house_prices")
                execute(connection, "DROP TABLE IF EXISTS hourly_auction_stats")
                execute(connection, oldHourlyAuctionStatsTableSql())
                execute(
                    connection,
                    """
                    ALTER TABLE hourly_auction_stats
                        ADD INDEX idx_date_and_house (connected_realm_id, date),
                        ADD INDEX idx_old_realm_item_date (connected_realm_id, item_id, date)
                    """.trimIndent(),
                )
                execute(connection, auctionHousePricesViewSql())
                execute(
                    connection,
                    """
                    INSERT INTO hourly_auction_stats (
                        connected_realm_id,
                        ah_type_id,
                        item_id,
                        date,
                        pet_species_id,
                        modifier_key,
                        bonus_key,
                        price03,
                        quantity03
                    ) VALUES (1084, 0, 19019, DATE '2026-04-06', -1, '', '', 123456, 10)
                    """.trimIndent(),
                )

                V4__align_hourly_auction_stats_indexes_and_storage().migrate(SimpleContext(connection))

                val createTable = queryString(connection, "SHOW CREATE TABLE hourly_auction_stats", "Create Table")
                val normalizedCreateTable = createTable.lowercase().replace("`", "").replace(Regex("\\s+"), " ")

                assertTrue(normalizedCreateTable.contains("max_rows=82300000"))
                assertTrue(normalizedCreateTable.contains("partition by hash (to_days(date))"))
                assertTrue(normalizedCreateTable.contains("partitions 31"))

                val secondaryIndexes =
                    queryPairs(
                        connection,
                        """
                        SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_csv
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'hourly_auction_stats'
                          AND index_name <> 'PRIMARY'
                        GROUP BY index_name
                        ORDER BY index_name
                        """.trimIndent(),
                    )

                assertEquals(
                    mapOf(
                        "idx_hourly_auction_stats_connected_realm_id_date" to "connected_realm_id,date",
                        "idx_hourly_auction_stats_connected_realm_id_item_id_date" to "connected_realm_id,item_id,date",
                    ),
                    secondaryIndexes,
                )

                val rowCount = queryInt(connection, "SELECT COUNT(*) FROM hourly_auction_stats")
                assertEquals(1, rowCount)

                val viewRowCount =
                    queryInt(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM v_auction_house_prices
                        WHERE connected_realm_id = 1084
                          AND item_id = 19019
                        """.trimIndent(),
                    )
                assertEquals(1, viewRowCount)
            } finally {
                execute(connection, "DROP VIEW IF EXISTS v_auction_house_prices")
                execute(connection, "DROP TABLE IF EXISTS hourly_auction_stats")
                execute(connection, "DROP TABLE IF EXISTS hourly_auction_stats_v4_new")
                execute(connection, "DROP TABLE IF EXISTS hourly_auction_stats_v4_old")
            }
        }
    }

    private fun databaseConnection(): Connection =
        DriverManager.getConnection(
            SharedTestContainers.mariaDbContainer.jdbcUrl,
            SharedTestContainers.mariaDbContainer.username,
            SharedTestContainers.mariaDbContainer.password,
        )

    private fun execute(
        connection: Connection,
        sql: String,
    ) {
        connection.createStatement().use { statement ->
            statement.execute(sql)
        }
    }

    private fun queryInt(
        connection: Connection,
        sql: String,
    ): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    private fun queryString(
        connection: Connection,
        sql: String,
        columnName: String,
    ): String =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                rs.next()
                rs.getString(columnName)
            }
        }

    private fun queryColumn(
        connection: Connection,
        sql: String,
    ): List<String> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.getString(1))
                    }
                }
            }
        }

    private fun queryPairs(
        connection: Connection,
        sql: String,
    ): Map<String, String> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(rs.getString(1), rs.getString(2))
                    }
                }
            }
        }

    private fun oldHourlyAuctionStatsTableSql(): String =
        buildString {
            appendLine("CREATE TABLE hourly_auction_stats (")
            appendLine("    connected_realm_id INT NOT NULL,")
            appendLine("    ah_type_id INT NOT NULL,")
            appendLine("    item_id INT NOT NULL,")
            appendLine("    date DATE NOT NULL,")
            appendLine("    pet_species_id INT NOT NULL,")
            appendLine("    modifier_key VARCHAR(255) NOT NULL DEFAULT '',")
            appendLine("    bonus_key VARCHAR(255) NOT NULL DEFAULT '',")
            for (hour in 0..23) {
                val paddedHour = hour.toString().padStart(2, '0')
                appendLine("    price$paddedHour BIGINT NULL,")
                appendLine("    quantity$paddedHour BIGINT NULL,")
            }
            appendLine(
                "    PRIMARY KEY (connected_realm_id, ah_type_id, item_id, date, pet_species_id, modifier_key, bonus_key)",
            )
            appendLine(")")
            append("ENGINE=InnoDB")
        }

    private fun auctionHousePricesViewSql(): String =
        buildString {
            appendLine("CREATE OR REPLACE VIEW v_auction_house_prices AS")
            for (hour in 0..23) {
                if (hour > 0) {
                    appendLine("UNION ALL")
                }
                val paddedHour = hour.toString().padStart(2, '0')
                appendLine(
                    """
                    SELECT connected_realm_id,
                           ah_type_id,
                           item_id,
                           pet_species_id,
                           modifier_key,
                           bonus_key,
                           TIMESTAMP(date, MAKETIME($hour, 0, 0)) AS auction_timestamp,
                           price$paddedHour AS price,
                           quantity$paddedHour AS quantity
                    FROM hourly_auction_stats
                    WHERE price$paddedHour IS NOT NULL OR quantity$paddedHour IS NOT NULL
                    """.trimIndent(),
                )
            }
        }

    private class SimpleContext(
        private val connection: Connection,
    ) : Context {
        override fun getConfiguration(): Configuration =
            throw UnsupportedOperationException("Not required for this test")

        override fun getConnection(): Connection = connection
    }
}
