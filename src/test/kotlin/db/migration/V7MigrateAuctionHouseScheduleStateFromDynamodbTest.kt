package db.migration

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseUpdateLogDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.StatsDynamo
import net.jonasmf.auctionengine.testsupport.container.SharedTestContainers
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import software.amazon.awssdk.services.dynamodb.model.TableStatus
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.temporal.ChronoUnit

class V7MigrateAuctionHouseScheduleStateFromDynamodbTest {
    @Test
    fun `should migrate auction houses and recent logs from DynamoDB into MariaDB idempotently`() {
        SharedTestContainers.startMariaDb()
        SharedTestContainers.startFloci()

        databaseConnection().use { connection ->
            dynamoDbClient().use { dynamoDbClient ->
                try {
                    recreateMariaTables(connection)
                    recreateDynamoTables(dynamoDbClient)
                    seedMariaData(connection)
                    seedDynamoData(dynamoDbClient)
                    configureMigrationProperties()

                    val migration = V7__migrate_auction_house_schedule_state_from_dynamodb()
                    migration.migrate(SimpleContext(connection))
                    migration.migrate(SimpleContext(connection))

                    assertEquals(2, queryInt(connection, "SELECT COUNT(*) FROM auction_house"))
                    assertEquals(2, queryInt(connection, "SELECT COUNT(*) FROM auction_house_file_log"))
                    assertEquals(2, queryInt(connection, "SELECT COUNT(*) FROM file_reference"))

                    val existingHouse =
                        queryRow(
                            connection,
                            """
                            SELECT id, connected_id, region, avg_delay, lowest_delay, highest_delay, update_attempts, stats_last_modified
                            FROM auction_house
                            WHERE id = 10
                            """.trimIndent(),
                        )
                    assertEquals(listOf("10", "101", "Korea", "47", "35", "115", "3", "999"), existingHouse)

                    val insertedHouse =
                        queryRow(
                            connection,
                            """
                            SELECT id, connected_id, region, avg_delay, lowest_delay, highest_delay, update_attempts, stats_last_modified
                            FROM auction_house
                            WHERE connected_id = 202
                            """.trimIndent(),
                        )
                    assertEquals(listOf("20", "202", "Europe", "60", "45", "90", "1", "1234"), insertedHouse)

                    assertEquals(
                        10,
                        queryInt(connection, "SELECT auction_house_id FROM connected_realm WHERE id = 101"),
                    )

                    val logRows =
                        queryRows(
                            connection,
                            """
                            SELECT l.auction_house_id, l.time_since_previous_dump, f.path, f.bucket_name
                            FROM auction_house_file_log l
                            JOIN file_reference f ON f.id = l.file_id
                            ORDER BY l.auction_house_id, l.last_modified
                            """.trimIndent(),
                        )
                    assertEquals(
                        listOf(
                            listOf("10", "2700000", "https://bucket.example/ah-101-recent.json", "bucket.example"),
                            listOf("20", "3600000", "https://bucket.example/ah-202-recent.json", "bucket.example"),
                        ),
                        logRows,
                    )
                } finally {
                    clearMigrationProperties()
                    dropDynamoTables(dynamoDbClient)
                    dropMariaTables(connection)
                }
            }
        }
    }

    @Test
    fun `should no-op when DynamoDB endpoint is not configured`() {
        SharedTestContainers.startMariaDb()

        databaseConnection().use { connection ->
            try {
                recreateMariaTables(connection)
                clearMigrationProperties()

                V7__migrate_auction_house_schedule_state_from_dynamodb().migrate(SimpleContext(connection))

                assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM auction_house"))
                assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM auction_house_file_log"))
                assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM file_reference"))
            } finally {
                clearMigrationProperties()
                dropMariaTables(connection)
            }
        }
    }

    private fun databaseConnection(): Connection =
        DriverManager.getConnection(
            SharedTestContainers.mariaDbContainer.jdbcUrl,
            SharedTestContainers.mariaDbContainer.username,
            SharedTestContainers.mariaDbContainer.password,
        )

    private fun dynamoDbClient(): DynamoDbClient =
        DynamoDbClient
            .builder()
            .endpointOverride(URI.create(flociEndpoint()))
            .region(
                software.amazon.awssdk.regions.Region
                    .of("eu-west-1"),
            ).credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test", "test"),
                ),
            ).build()

    private fun recreateMariaTables(connection: Connection) {
        dropMariaTables(connection)
        execute(
            connection,
            """
            CREATE TABLE file_reference (
                id BIGINT NOT NULL AUTO_INCREMENT,
                path VARCHAR(255) NOT NULL DEFAULT '',
                bucket_name VARCHAR(255) NOT NULL DEFAULT '',
                created DATETIME(6) NULL,
                size DOUBLE NOT NULL DEFAULT 0,
                PRIMARY KEY (id)
            )
            """.trimIndent(),
        )
        execute(
            connection,
            """
            CREATE TABLE auction_house (
                id INT NOT NULL,
                connected_id INT NOT NULL DEFAULT 0,
                region VARCHAR(32) NOT NULL DEFAULT 'Europe',
                auto_update BIT(1) NOT NULL DEFAULT b'0',
                last_modified DATETIME(6) NULL,
                last_requested DATETIME(6) NULL,
                next_update DATETIME(6) NULL,
                lowest_delay BIGINT NULL,
                avg_delay BIGINT NULL,
                highest_delay BIGINT NULL,
                game_build INT NULL DEFAULT 0,
                last_daily_price_update DATETIME(6) NULL,
                last_history_delete_event DATETIME(6) NULL,
                last_history_delete_event_daily DATETIME(6) NULL,
                last_stats_insert DATETIME(6) NULL,
                last_trend_update_initiation DATETIME(6) NULL,
                stats_last_modified BIGINT NULL DEFAULT 0,
                update_attempts INT NULL DEFAULT 0,
                PRIMARY KEY (id),
                UNIQUE KEY ux_auction_house_connected_id (connected_id)
            )
            """.trimIndent(),
        )
        execute(
            connection,
            """
            CREATE TABLE connected_realm (
                id INT NOT NULL,
                auction_house_id INT NOT NULL,
                PRIMARY KEY (id)
            )
            """.trimIndent(),
        )
        execute(
            connection,
            """
            CREATE TABLE auction_house_file_log (
                id BIGINT NOT NULL AUTO_INCREMENT,
                last_modified DATETIME(6) NULL,
                time_since_previous_dump BIGINT NOT NULL DEFAULT 0,
                file_id BIGINT NOT NULL,
                auction_house_id INT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY ux_auction_house_file_log_house_last_modified (auction_house_id, last_modified)
            )
            """.trimIndent(),
        )
    }

    private fun dropMariaTables(connection: Connection) {
        execute(connection, "DROP TABLE IF EXISTS auction_house_file_log")
        execute(connection, "DROP TABLE IF EXISTS connected_realm")
        execute(connection, "DROP TABLE IF EXISTS auction_house")
        execute(connection, "DROP TABLE IF EXISTS file_reference")
    }

    private fun recreateDynamoTables(dynamoDbClient: DynamoDbClient) {
        dropDynamoTables(dynamoDbClient)
        dynamoDbClient.createTable(
            CreateTableRequest
                .builder()
                .tableName("wah_auction_houses")
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                    AttributeDefinition
                        .builder()
                        .attributeName("id")
                        .attributeType(ScalarAttributeType.N)
                        .build(),
                ).keySchema(
                    KeySchemaElement
                        .builder()
                        .attributeName("id")
                        .keyType(KeyType.HASH)
                        .build(),
                ).build(),
        )
        dynamoDbClient.createTable(
            CreateTableRequest
                .builder()
                .tableName("wah_auction_houses_update_log")
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                    AttributeDefinition
                        .builder()
                        .attributeName("id")
                        .attributeType(ScalarAttributeType.N)
                        .build(),
                    AttributeDefinition
                        .builder()
                        .attributeName(
                            "lastModified",
                        ).attributeType(ScalarAttributeType.N)
                        .build(),
                ).keySchema(
                    KeySchemaElement
                        .builder()
                        .attributeName("id")
                        .keyType(KeyType.HASH)
                        .build(),
                    KeySchemaElement
                        .builder()
                        .attributeName("lastModified")
                        .keyType(KeyType.RANGE)
                        .build(),
                ).build(),
        )
        waitForTable(dynamoDbClient, "wah_auction_houses")
        waitForTable(dynamoDbClient, "wah_auction_houses_update_log")
    }

    private fun dropDynamoTables(dynamoDbClient: DynamoDbClient) {
        listOf("wah_auction_houses_update_log", "wah_auction_houses").forEach { tableName ->
            try {
                dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName(tableName).build())
            } catch (_: ResourceNotFoundException) {
                // Already gone.
            }
        }
    }

    private fun waitForTable(
        dynamoDbClient: DynamoDbClient,
        tableName: String,
    ) {
        repeat(20) {
            val status =
                dynamoDbClient
                    .describeTable(DescribeTableRequest.builder().tableName(tableName).build())
                    .table()
                    .tableStatus()
            if (status == TableStatus.ACTIVE) {
                return
            }
            Thread.sleep(250)
        }
        error("Timed out waiting for Dynamo table $tableName to become active")
    }

    private fun seedMariaData(connection: Connection) {
        execute(
            connection,
            """
            INSERT INTO auction_house (
                id,
                connected_id,
                region,
                auto_update,
                avg_delay,
                lowest_delay,
                highest_delay,
                update_attempts,
                stats_last_modified
            ) VALUES (10, 101, 'Europe', b'0', 0, 0, 0, 0, 0)
            """.trimIndent(),
        )
        execute(connection, "INSERT INTO connected_realm (id, auction_house_id) VALUES (101, 10)")
    }

    private fun seedDynamoData(dynamoDbClient: DynamoDbClient) {
        val enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build()
        val auctionHouseTable =
            enhancedClient.table(
                "wah_auction_houses",
                TableSchema.fromBean(AuctionHouseDynamo::class.java),
            )
        val updateLogTable =
            enhancedClient.table(
                "wah_auction_houses_update_log",
                TableSchema.fromBean(AuctionHouseUpdateLogDynamo::class.java),
            )

        auctionHouseTable.putItem(
            AuctionHouseDynamo(
                id = 10,
                connectedId = 101,
                region = Region.Korea,
                autoUpdate = true,
                avgDelay = 47,
                gameBuild = 11,
                highestDelay = 115,
                lastModified = Instant.parse("2026-04-14T09:55:00Z"),
                lowestDelay = 35,
                nextUpdate = Instant.parse("2026-04-14T10:30:00Z"),
                stats = StatsDynamo(lastModified = 999, url = "https://bucket.example/stats-101.json"),
                updateAttempts = 3,
                url = "https://bucket.example/ah-101-latest.json",
                size = 123.4,
            ),
        )
        auctionHouseTable.putItem(
            AuctionHouseDynamo(
                id = 20,
                connectedId = 202,
                region = Region.Europe,
                autoUpdate = true,
                avgDelay = 60,
                gameBuild = 12,
                highestDelay = 90,
                lastModified = Instant.parse("2026-04-14T11:00:00Z"),
                lowestDelay = 45,
                nextUpdate = Instant.parse("2026-04-14T12:00:00Z"),
                stats = StatsDynamo(lastModified = 1234, url = "https://bucket.example/stats-202.json"),
                updateAttempts = 1,
                url = "https://bucket.example/ah-202-latest.json",
                size = 321.0,
            ),
        )

        updateLogTable.putItem(
            AuctionHouseUpdateLogDynamo(
                id = 101,
                lastModified = Instant.now().minus(20, ChronoUnit.DAYS),
                size = 1.0,
                timeSincePreviousDump = 1800000,
                url = "https://bucket.example/ah-101-old.json",
            ),
        )
        updateLogTable.putItem(
            AuctionHouseUpdateLogDynamo(
                id = 101,
                lastModified = Instant.now().minus(3, ChronoUnit.DAYS),
                size = 2.5,
                timeSincePreviousDump = 2700000,
                url = "https://bucket.example/ah-101-recent.json",
            ),
        )
        updateLogTable.putItem(
            AuctionHouseUpdateLogDynamo(
                id = 202,
                lastModified = Instant.now().minus(1, ChronoUnit.DAYS),
                size = 3.5,
                timeSincePreviousDump = 3600000,
                url = "https://bucket.example/ah-202-recent.json",
            ),
        )
    }

    private fun configureMigrationProperties() {
        System.setProperty("spring.cloud.aws.dynamodb.endpoint", flociEndpoint())
        System.setProperty("spring.cloud.aws.credentials.access-key", "test")
        System.setProperty("spring.cloud.aws.credentials.secret-key", "test")
        System.setProperty("spring.cloud.aws.region.static", "eu-west-1")
    }

    private fun clearMigrationProperties() {
        listOf(
            "spring.cloud.aws.dynamodb.endpoint",
            "spring.cloud.aws.credentials.access-key",
            "spring.cloud.aws.credentials.secret-key",
            "spring.cloud.aws.region.static",
        ).forEach(System::clearProperty)
    }

    private fun flociEndpoint(): String =
        "http://${SharedTestContainers.flociContainer.host}:${SharedTestContainers.flociContainer.getMappedPort(4566)}"

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

    private fun queryRow(
        connection: Connection,
        sql: String,
    ): List<String> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                assertFalse(rs.next().not())
                List(rs.metaData.columnCount) { index -> rs.getString(index + 1) }
            }
        }

    private fun queryRows(
        connection: Connection,
        sql: String,
    ): List<List<String>> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(List(rs.metaData.columnCount) { index -> rs.getString(index + 1) })
                    }
                }
            }
        }

    private class SimpleContext(
        private val connection: Connection,
    ) : Context {
        override fun getConfiguration(): Configuration = throw UnsupportedOperationException("Not required for test")

        override fun getConnection(): Connection = connection
    }
}
