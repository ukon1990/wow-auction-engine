package db.migration

import net.jonasmf.auctionengine.testsupport.container.SharedTestContainers
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

class V5MigrateAuctionHouseScheduleStateToMariadbTest {
    @Test
    fun `should backfill orphaned auction house connected ids before adding unique index`() {
        SharedTestContainers.startMariaDb()

        databaseConnection().use { connection ->
            try {
                execute(connection, "DROP TABLE IF EXISTS flyway_schema_history")
                execute(connection, "DROP TABLE IF EXISTS auction_house_file_log")
                execute(connection, "DROP TABLE IF EXISTS connected_realm")
                execute(connection, "DROP TABLE IF EXISTS auction_house")
                execute(connection, "DROP TABLE IF EXISTS file_reference")

                execute(connection, oldFileReferenceTableSql())
                execute(connection, oldAuctionHouseTableSql())
                execute(connection, oldConnectedRealmTableSql())
                execute(connection, oldAuctionHouseFileLogTableSql())

                execute(
                    connection,
                    """
                    INSERT INTO auction_house (id, next_update, average_delay, failed_attempts)
                    VALUES
                        (10, TIMESTAMP '2026-04-14 10:00:00', 45, 2),
                        (20, TIMESTAMP '2026-04-14 11:00:00', 30, 1)
                    """.trimIndent(),
                )
                execute(connection, "INSERT INTO connected_realm (id, auction_house_id) VALUES (101, 10)")
                execute(
                    connection,
                    "INSERT INTO auction_house_file_log (id, `timestamp`) VALUES (1, TIMESTAMP '2026-04-14 09:55:00')",
                )

                Flyway
                    .configure()
                    .dataSource(
                        SharedTestContainers.mariaDbContainer.jdbcUrl,
                        SharedTestContainers.mariaDbContainer.username,
                        SharedTestContainers.mariaDbContainer.password,
                    ).baselineOnMigrate(true)
                    .baselineVersion("4")
                    .locations("classpath:db/migration")
                    .load()
                    .migrate()

                val connectedIds =
                    queryPairs(
                        connection,
                        "SELECT id, connected_id FROM auction_house ORDER BY id",
                    )
                assertEquals(mapOf(10 to 101, 20 to 20), connectedIds)

                val duplicateCount =
                    queryInt(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM (
                            SELECT connected_id
                            FROM auction_house
                            GROUP BY connected_id
                            HAVING COUNT(*) > 1
                        ) duplicates
                        """.trimIndent(),
                    )
                assertEquals(0, duplicateCount)

                val indexNames =
                    queryStrings(
                        connection,
                        """
                        SELECT index_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'auction_house'
                          AND index_name = 'ux_auction_house_connected_id'
                        """.trimIndent(),
                    )
                assertEquals(listOf("ux_auction_house_connected_id"), indexNames)

                val migratedValues =
                    queryRow(
                        connection,
                        """
                        SELECT avg_delay, update_attempts, region
                        FROM auction_house
                        WHERE id = 10
                        """.trimIndent(),
                    )
                assertEquals("45", migratedValues[0])
                assertEquals("2", migratedValues[1])
                assertEquals("Europe", migratedValues[2])

                val backfilledLogTimestamp =
                    queryString(
                        connection,
                        "SELECT DATE_FORMAT(last_modified, '%Y-%m-%d %H:%i:%s') FROM auction_house_file_log WHERE id = 1",
                    )
                assertTrue(backfilledLogTimestamp.startsWith("2026-04-14 09:55:00"))
            } finally {
                execute(connection, "DROP TABLE IF EXISTS flyway_schema_history")
                execute(connection, "DROP TABLE IF EXISTS auction_house_file_log")
                execute(connection, "DROP TABLE IF EXISTS connected_realm")
                execute(connection, "DROP TABLE IF EXISTS auction_house")
                execute(connection, "DROP TABLE IF EXISTS file_reference")
            }
        }
    }

    @Test
    fun `should prune duplicate nonzero connected ids before creating unique index`() {
        SharedTestContainers.startMariaDb()

        databaseConnection().use { connection ->
            try {
                execute(connection, "DROP TABLE IF EXISTS flyway_schema_history")
                execute(connection, "DROP TABLE IF EXISTS auction_house_file_log")
                execute(connection, "DROP TABLE IF EXISTS connected_realm")
                execute(connection, "DROP TABLE IF EXISTS auction_house")
                execute(connection, "DROP TABLE IF EXISTS file_reference")

                execute(connection, oldFileReferenceTableSql())
                execute(connection, oldAuctionHouseTableSql())
                execute(connection, oldConnectedRealmTableSql())
                execute(connection, oldAuctionHouseFileLogTableSql())

                execute(connection, "ALTER TABLE auction_house ADD COLUMN connected_id INT NOT NULL DEFAULT 0")
                execute(connection, "ALTER TABLE auction_house ADD COLUMN region VARCHAR(32) NOT NULL DEFAULT 'Europe'")
                execute(
                    connection,
                    """
                    INSERT INTO auction_house (id, connected_id, region, next_update)
                    VALUES
                        (10, 3656, 'Europe', TIMESTAMP '2026-04-14 10:00:00'),
                        (20, 3656, 'Europe', TIMESTAMP '2026-04-14 11:00:00')
                    """.trimIndent(),
                )
                execute(connection, "INSERT INTO connected_realm (id, auction_house_id) VALUES (3656, 10)")
                execute(connection, "ALTER TABLE auction_house_file_log ADD COLUMN auction_house_id INT NULL")
                execute(
                    connection,
                    """
                    INSERT INTO auction_house_file_log (id, auction_house_id, `timestamp`)
                    VALUES (1, 20, TIMESTAMP '2026-04-14 09:55:00')
                    """.trimIndent(),
                )

                Flyway
                    .configure()
                    .dataSource(
                        SharedTestContainers.mariaDbContainer.jdbcUrl,
                        SharedTestContainers.mariaDbContainer.username,
                        SharedTestContainers.mariaDbContainer.password,
                    ).baselineOnMigrate(true)
                    .baselineVersion("4")
                    .locations("classpath:db/migration")
                    .load()
                    .migrate()

                assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM auction_house WHERE connected_id = 3656"))
                assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM auction_house"))
                assertEquals(10, queryInt(connection, "SELECT auction_house_id FROM connected_realm WHERE id = 3656"))
                assertEquals(
                    10,
                    queryInt(connection, "SELECT auction_house_id FROM auction_house_file_log WHERE id = 1"),
                )
            } finally {
                execute(connection, "DROP TABLE IF EXISTS flyway_schema_history")
                execute(connection, "DROP TABLE IF EXISTS auction_house_file_log")
                execute(connection, "DROP TABLE IF EXISTS connected_realm")
                execute(connection, "DROP TABLE IF EXISTS auction_house")
                execute(connection, "DROP TABLE IF EXISTS file_reference")
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
    ): String =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                rs.next()
                rs.getString(1)
            }
        }

    private fun queryStrings(
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
    ): Map<Int, Int> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(rs.getInt(1), rs.getInt(2))
                    }
                }
            }
        }

    private fun queryRow(
        connection: Connection,
        sql: String,
    ): List<String> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                rs.next()
                listOf(rs.getString(1), rs.getString(2), rs.getString(3))
            }
        }

    private fun oldFileReferenceTableSql(): String =
        """
        CREATE TABLE file_reference (
            id BIGINT NOT NULL AUTO_INCREMENT,
            path VARCHAR(255) NOT NULL DEFAULT '',
            bucket_name VARCHAR(255) NOT NULL DEFAULT '',
            created DATETIME(6) NULL,
            PRIMARY KEY (id)
        )
        """.trimIndent()

    private fun oldAuctionHouseTableSql(): String =
        """
        CREATE TABLE auction_house (
            id INT NOT NULL AUTO_INCREMENT,
            next_update DATETIME(6) NULL,
            average_delay BIGINT NULL,
            failed_attempts INT NULL,
            PRIMARY KEY (id)
        )
        """.trimIndent()

    private fun oldConnectedRealmTableSql(): String =
        """
        CREATE TABLE connected_realm (
            id INT NOT NULL,
            auction_house_id INT NOT NULL,
            PRIMARY KEY (id)
        )
        """.trimIndent()

    private fun oldAuctionHouseFileLogTableSql(): String =
        """
        CREATE TABLE auction_house_file_log (
            id BIGINT NOT NULL AUTO_INCREMENT,
            `timestamp` DATETIME(6) NULL,
            PRIMARY KEY (id)
        )
        """.trimIndent()
}
