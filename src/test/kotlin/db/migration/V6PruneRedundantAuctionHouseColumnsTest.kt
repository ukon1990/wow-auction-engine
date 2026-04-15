package db.migration

import net.jonasmf.auctionengine.testsupport.container.SharedTestContainers
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

class V6PruneRedundantAuctionHouseColumnsTest {
    @Test
    fun `should drop redundant auction house columns after preserving canonical values`() {
        SharedTestContainers.startMariaDb()

        databaseConnection().use { connection ->
            try {
                dropTables(connection)
                execute(connection, oldAuctionHouseTableSql())
                execute(
                    connection,
                    """
                    INSERT INTO auction_house (
                        id,
                        connected_id,
                        region,
                        avg_delay,
                        update_attempts,
                        average_delay,
                        failed_attempts,
                        realm_slugs,
                        realms_json
                    ) VALUES (1, 1001, 'Europe', 0, 0, 45, 3, 'a,b', '[{"slug":"a"}]')
                    """.trimIndent(),
                )

                Flyway
                    .configure()
                    .dataSource(
                        SharedTestContainers.mariaDbContainer.jdbcUrl,
                        SharedTestContainers.mariaDbContainer.username,
                        SharedTestContainers.mariaDbContainer.password,
                    ).baselineOnMigrate(true)
                    .baselineVersion("5")
                    .locations("classpath:db/migration")
                    .load()
                    .migrate()

                assertEquals(45, queryInt(connection, "SELECT avg_delay FROM auction_house WHERE id = 1"))
                assertEquals(3, queryInt(connection, "SELECT update_attempts FROM auction_house WHERE id = 1"))
                assertEquals(
                    0,
                    countColumns(connection, "average_delay", "failed_attempts", "realm_slugs", "realms_json"),
                )
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
                "auction_house_file_log",
                "connected_realm",
                "auction_house",
                "file_reference",
            ).forEach { execute(connection, "DROP TABLE IF EXISTS $it") }
        } finally {
            execute(connection, "SET FOREIGN_KEY_CHECKS = 1")
        }
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
        vararg columnNames: String,
    ): Int {
        val quoted = columnNames.joinToString(",") { "'" + it + "'" }
        return queryInt(
            connection,
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'auction_house'
              AND column_name IN ($quoted)
            """.trimIndent(),
        )
    }

    private fun oldAuctionHouseTableSql(): String =
        """
        CREATE TABLE auction_house (
            id INT NOT NULL AUTO_INCREMENT,
            connected_id INT NOT NULL,
            region VARCHAR(32) NOT NULL,
            avg_delay BIGINT NULL,
            update_attempts INT NULL,
            average_delay BIGINT NULL,
            failed_attempts INT NULL,
            realm_slugs VARCHAR(1024) NULL,
            realms_json LONGTEXT NULL,
            PRIMARY KEY (id)
        )
        """.trimIndent()
}
