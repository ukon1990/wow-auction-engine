package db.migration

import net.jonasmf.auctionengine.testsupport.container.SharedTestContainers
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

class V11BulkSnapshotCurrentAuctionsTest {
    @Test
    fun `should add bulk snapshot schema and normalize legacy auction data`() {
        SharedTestContainers.startMariaDb()

        databaseConnection().use { connection ->
            try {
                dropTables(connection)
                createLegacySchema(connection)
                seedLegacyRows(connection)

                Flyway
                    .configure()
                    .dataSource(
                        SharedTestContainers.mariaDbContainer.jdbcUrl,
                        SharedTestContainers.mariaDbContainer.username,
                        SharedTestContainers.mariaDbContainer.password,
                    ).baselineOnMigrate(true)
                    .baselineVersion("10")
                    .locations("classpath:db/migration")
                    .load()
                    .migrate()

                assertEquals(1, countColumns(connection, "auction", "deleted_at"))
                assertEquals(1, countColumns(connection, "auction_item", "variant_hash"))
                assertTrue(countIndexes(connection, "auction_item", "uk_auction_item_variant_hash") >= 1)
                assertTrue(countIndexes(connection, "auction_item", "idx_auction_item_item_id") >= 1)
                assertTrue(countIndexes(connection, "auction", "idx_auction_connected_realm_update_deleted") >= 1)
                assertTrue(countIndexes(connection, "auction", "idx_auction_deleted_at") >= 1)
                assertTrue(
                    countIndexes(connection, "auction_item_modifier", "uk_auction_item_modifier_type_value") >= 1,
                )
                assertTrue(tableExists(connection, "auction_item_modifier_link"))
                assertEquals(0, countTables(connection, "auction_item_modifiers"))

                assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM auction_item_modifier"))
                assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM auction_item"))
                assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM auction_item_modifier_link"))
                assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM auction WHERE item_id = 1"))
            } finally {
                dropTables(connection)
            }
        }
    }

    private fun databaseConnection(): Connection =
        DriverManager.getConnection(
            SharedTestContainers.mariaDbContainer.jdbcUrl,
            SharedTestContainers.mariaDbContainer.username,
            SharedTestContainers.mariaDbContainer.password,
        )

    private fun dropTables(connection: Connection) {
        execute(connection, "SET FOREIGN_KEY_CHECKS = 0")
        try {
            listOf(
                "flyway_schema_history",
                "auction",
                "auction_item_modifier_link",
                "auction_item_modifiers",
                "auction_item",
                "auction_item_modifier",
                "connected_realm_update_history",
                "connected_realm",
            ).forEach { execute(connection, "DROP TABLE IF EXISTS $it") }
        } finally {
            execute(connection, "SET FOREIGN_KEY_CHECKS = 1")
        }
    }

    private fun createLegacySchema(connection: Connection) {
        execute(
            connection,
            """
            CREATE TABLE connected_realm (
                id INT NOT NULL,
                PRIMARY KEY (id)
            )
            """.trimIndent(),
        )
        execute(
            connection,
            """
            CREATE TABLE connected_realm_update_history (
                id BIGINT NOT NULL AUTO_INCREMENT,
                auction_count INT NOT NULL,
                last_modified DATETIME(6) NULL,
                update_timestamp DATETIME(6) NULL,
                completed_timestamp DATETIME(6) NULL,
                connected_realm_id INT NOT NULL,
                PRIMARY KEY (id),
                CONSTRAINT fk_cruh_connected_realm
                    FOREIGN KEY (connected_realm_id) REFERENCES connected_realm (id)
            )
            """.trimIndent(),
        )
        execute(
            connection,
            """
            CREATE TABLE auction_item_modifier (
                id BIGINT NOT NULL AUTO_INCREMENT,
                type VARCHAR(255) NOT NULL,
                value INT NOT NULL,
                PRIMARY KEY (id)
            )
            """.trimIndent(),
        )
        execute(
            connection,
            """
            CREATE TABLE auction_item (
                id BIGINT NOT NULL AUTO_INCREMENT,
                item_id INT NOT NULL,
                bonus_lists VARCHAR(255) NOT NULL DEFAULT '',
                context INT NULL,
                pet_breed_id INT NULL,
                pet_level INT NULL,
                pet_quality_id INT NULL,
                pet_species_id INT NULL,
                PRIMARY KEY (id)
            )
            """.trimIndent(),
        )
        execute(
            connection,
            """
            CREATE TABLE auction_item_modifiers (
                auction_item_id BIGINT NOT NULL,
                modifiers_id BIGINT NOT NULL
            )
            """.trimIndent(),
        )
        execute(
            connection,
            """
            CREATE TABLE auction (
                id BIGINT NOT NULL,
                connected_realm_id INT NOT NULL,
                item_id BIGINT NOT NULL,
                quantity BIGINT NOT NULL,
                bid BIGINT NULL,
                unit_price BIGINT NULL,
                time_left INT NOT NULL,
                buyout BIGINT NULL,
                first_seen DATETIME(6) NULL,
                last_seen DATETIME(6) NULL,
                update_history_id BIGINT NOT NULL,
                PRIMARY KEY (id, connected_realm_id),
                CONSTRAINT fk_auction_connected_realm
                    FOREIGN KEY (connected_realm_id) REFERENCES connected_realm (id),
                CONSTRAINT fk_auction_item
                    FOREIGN KEY (item_id) REFERENCES auction_item (id),
                CONSTRAINT fk_auction_update_history
                    FOREIGN KEY (update_history_id) REFERENCES connected_realm_update_history (id)
            )
            """.trimIndent(),
        )
    }

    private fun seedLegacyRows(connection: Connection) {
        execute(connection, "INSERT INTO connected_realm (id) VALUES (1)")
        execute(
            connection,
            """
            INSERT INTO connected_realm_update_history (
                id, auction_count, last_modified, update_timestamp, completed_timestamp, connected_realm_id
            ) VALUES (
                1, 1, '2026-04-10 10:00:00.000000', '2026-04-10 10:00:00.000000', '2026-04-10 10:00:00.000000', 1
            )
            """.trimIndent(),
        )
        execute(
            connection,
            """
            INSERT INTO auction_item_modifier (id, type, value) VALUES
                (1, 'ITEM_LEVEL', 489),
                (2, 'ITEM_LEVEL', 489)
            """.trimIndent(),
        )
        execute(
            connection,
            """
            INSERT INTO auction_item (id, item_id, bonus_lists, context) VALUES
                (1, 211297, '12251,12252,12499', 52),
                (2, 211297, '12251,12252,12499', 52)
            """.trimIndent(),
        )
        execute(
            connection,
            """
            INSERT INTO auction_item_modifiers (auction_item_id, modifiers_id) VALUES
                (1, 1),
                (2, 2)
            """.trimIndent(),
        )
        execute(
            connection,
            """
            INSERT INTO auction (
                id, connected_realm_id, item_id, quantity, bid, unit_price, time_left, buyout, first_seen, last_seen, update_history_id
            ) VALUES (
                101, 1, 2, 1, NULL, 1599900, 3, 1599900, '2026-04-10 10:00:00.000000', '2026-04-10 10:00:00.000000', 1
            )
            """.trimIndent(),
        )
    }

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

    private fun countColumns(
        connection: Connection,
        tableName: String,
        columnName: String,
    ): Int =
        queryInt(
            connection,
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = '$tableName'
              AND column_name = '$columnName'
            """.trimIndent(),
        )

    private fun countIndexes(
        connection: Connection,
        tableName: String,
        indexName: String,
    ): Int =
        queryInt(
            connection,
            """
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = '$tableName'
              AND index_name = '$indexName'
            """.trimIndent(),
        )

    private fun countTables(
        connection: Connection,
        tableName: String,
    ): Int =
        queryInt(
            connection,
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = '$tableName'
            """.trimIndent(),
        )

    private fun tableExists(
        connection: Connection,
        tableName: String,
    ): Boolean = countTables(connection, tableName) == 1
}
