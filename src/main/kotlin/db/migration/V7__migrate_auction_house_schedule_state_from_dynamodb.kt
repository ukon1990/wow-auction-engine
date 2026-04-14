package db.migration

import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseUpdateLogDynamo
import net.jonasmf.auctionengine.repository.dynamodb.AUCTION_HOUSE_TABLE_NAME
import net.jonasmf.auctionengine.repository.dynamodb.AUCTION_HOUSE_UPDATE_LOG_TABLE_NAME
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import java.net.URI
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

@Suppress("ClassName")
class V7__migrate_auction_house_schedule_state_from_dynamodb : BaseJavaMigration() {
    private val log = LoggerFactory.getLogger(V7__migrate_auction_house_schedule_state_from_dynamodb::class.java)
    private val applicationProperties by lazy { loadApplicationProperties() }

    override fun canExecuteInTransaction(): Boolean = false

    override fun migrate(context: Context) {
        val endpoint = property("spring.cloud.aws.dynamodb.endpoint", "WAE_DYNAMODB_ENDPOINT").trim()
        if (endpoint.isBlank()) {
            log.info("Skipping DynamoDB schedule-state backfill because no DynamoDB endpoint is configured")
            return
        }

        val accessKey = property("spring.cloud.aws.credentials.access-key", "AWS_ACCESS_KEY").ifBlank { "test" }
        val secretKey = property("spring.cloud.aws.credentials.secret-key", "AWS_SECRET_KEY").ifBlank { "test" }
        val regionName = property("spring.cloud.aws.region.static", "WAE_AWS_REGION").ifBlank { "eu-west-1" }
        val logCutoff = Instant.now().minus(14, ChronoUnit.DAYS)

        DynamoDbClient
            .builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(regionName))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey),
                ),
            ).build()
            .use { dynamoDbClient ->
                val enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build()
                val auctionHouses = loadAuctionHouses(enhancedClient)
                val updateLogs = loadUpdateLogs(enhancedClient).filter { it.lastModified >= logCutoff }

                if (auctionHouses.isEmpty() && updateLogs.isEmpty()) {
                    log.info("No DynamoDB auction-house schedule state found to migrate")
                    return
                }

                val connection = context.connection
                val houseIdsByConnectedId = mutableMapOf<Int, Int>()

                auctionHouses.forEach { item ->
                    val connectedId = item.connectedId.takeIf { it != 0 } ?: item.id ?: return@forEach
                    houseIdsByConnectedId[connectedId] = upsertAuctionHouse(connection, connectedId, item)
                }

                updateLogs
                    .sortedWith(compareBy<AuctionHouseUpdateLogDynamo>({ it.id }, { it.lastModified }))
                    .forEach { item ->
                        val auctionHouseId =
                            houseIdsByConnectedId.getOrPut(item.id) {
                                findOrCreateAuctionHouseId(connection, item.id)
                            }
                        upsertAuctionHouseFileLog(connection, auctionHouseId, item)
                    }

                log.info(
                    "Migrated {} auction-house rows and {} update-log rows from DynamoDB into MariaDB",
                    houseIdsByConnectedId.size,
                    updateLogs.size,
                )
            }
    }

    private fun loadAuctionHouses(enhancedClient: DynamoDbEnhancedClient): List<AuctionHouseDynamo> =
        try {
            enhancedClient
                .table(AUCTION_HOUSE_TABLE_NAME, TableSchema.fromBean(AuctionHouseDynamo::class.java))
                .scan()
                .items()
                .toList()
        } catch (_: ResourceNotFoundException) {
            log.info("Skipping DynamoDB auction-house import because table {} does not exist", AUCTION_HOUSE_TABLE_NAME)
            emptyList()
        }

    private fun loadUpdateLogs(enhancedClient: DynamoDbEnhancedClient): List<AuctionHouseUpdateLogDynamo> =
        try {
            enhancedClient
                .table(
                    AUCTION_HOUSE_UPDATE_LOG_TABLE_NAME,
                    TableSchema.fromBean(AuctionHouseUpdateLogDynamo::class.java),
                ).scan()
                .items()
                .toList()
        } catch (_: ResourceNotFoundException) {
            log.info(
                "Skipping DynamoDB update-log import because table {} does not exist",
                AUCTION_HOUSE_UPDATE_LOG_TABLE_NAME,
            )
            emptyList()
        }

    private fun upsertAuctionHouse(
        connection: Connection,
        connectedId: Int,
        item: AuctionHouseDynamo,
    ): Int {
        val preferredId = findPreferredAuctionHouseId(connection, connectedId) ?: item.id ?: connectedId
        val existingByConnectedId = findExistingAuctionHouseId(connection, connectedId)
        val targetId = existingByConnectedId ?: preferredId

        if (auctionHouseExists(connection, targetId)) {
            connection
                .prepareStatement(
                    """
                    UPDATE auction_house
                    SET connected_id = ?,
                        region = ?,
                        auto_update = ?,
                        avg_delay = ?,
                        game_build = ?,
                        highest_delay = ?,
                        last_daily_price_update = ?,
                        last_history_delete_event = ?,
                        last_history_delete_event_daily = ?,
                        last_modified = ?,
                        last_requested = ?,
                        last_stats_insert = ?,
                        last_trend_update_initiation = ?,
                        lowest_delay = ?,
                        next_update = ?,
                        stats_last_modified = ?,
                        update_attempts = ?
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    bindAuctionHouse(statement, connectedId, item, targetId)
                    statement.executeUpdate()
                }
        } else {
            connection
                .prepareStatement(
                    """
                    INSERT INTO auction_house (
                        id,
                        connected_id,
                        region,
                        auto_update,
                        avg_delay,
                        game_build,
                        highest_delay,
                        last_daily_price_update,
                        last_history_delete_event,
                        last_history_delete_event_daily,
                        last_modified,
                        last_requested,
                        last_stats_insert,
                        last_trend_update_initiation,
                        lowest_delay,
                        next_update,
                        stats_last_modified,
                        update_attempts
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, targetId)
                    bindAuctionHouse(statement, connectedId, item, null, startIndex = 2)
                    statement.executeUpdate()
                }
        }

        updateConnectedRealmAuctionHouseId(connection, connectedId, targetId)
        return targetId
    }

    private fun bindAuctionHouse(
        statement: PreparedStatement,
        connectedId: Int,
        item: AuctionHouseDynamo,
        targetId: Int?,
        startIndex: Int = 1,
    ) {
        statement.setInt(startIndex, connectedId)
        statement.setString(startIndex + 1, item.region.name)
        statement.setBoolean(startIndex + 2, item.autoUpdate)
        statement.setLong(startIndex + 3, item.avgDelay)
        statement.setInt(startIndex + 4, item.gameBuild)
        statement.setLong(startIndex + 5, item.highestDelay)
        statement.setTimestamp(startIndex + 6, item.lastDailyPriceUpdate?.let(Timestamp::from))
        statement.setTimestamp(startIndex + 7, item.lastHistoryDeleteEvent?.let(Timestamp::from))
        statement.setTimestamp(startIndex + 8, item.lastHistoryDeleteEventDaily?.let(Timestamp::from))
        statement.setTimestamp(startIndex + 9, item.lastModified?.let(Timestamp::from))
        statement.setTimestamp(startIndex + 10, item.lastRequested?.let(Timestamp::from))
        statement.setTimestamp(startIndex + 11, item.lastStatsInsert?.let(Timestamp::from))
        statement.setTimestamp(startIndex + 12, item.lastTrendUpdateInitiation?.let(Timestamp::from))
        statement.setLong(startIndex + 13, item.lowestDelay)
        statement.setTimestamp(startIndex + 14, item.nextUpdate?.let(Timestamp::from))
        statement.setLong(startIndex + 15, item.stats.lastModified)
        statement.setInt(startIndex + 16, item.updateAttempts)
        if (targetId != null) {
            statement.setInt(startIndex + 17, targetId)
        }
    }

    private fun upsertAuctionHouseFileLog(
        connection: Connection,
        auctionHouseId: Int,
        item: AuctionHouseUpdateLogDynamo,
    ) {
        if (auctionHouseFileLogExists(connection, auctionHouseId, item.lastModified)) {
            return
        }

        val fileReferenceId = insertFileReference(connection, item.url, item.size, item.lastModified)

        connection
            .prepareStatement(
                """
                INSERT INTO auction_house_file_log (
                    last_modified,
                    time_since_previous_dump,
                    file_id,
                    auction_house_id
                ) VALUES (?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.from(item.lastModified))
                statement.setLong(2, item.timeSincePreviousDump)
                statement.setLong(3, fileReferenceId)
                statement.setInt(4, auctionHouseId)
                statement.executeUpdate()
            }
    }

    private fun insertFileReference(
        connection: Connection,
        path: String,
        size: Double,
        created: Instant,
    ): Long =
        connection
            .prepareStatement(
                "INSERT INTO file_reference (path, bucket_name, created, size) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, path)
                statement.setString(2, extractBucketName(path))
                statement.setTimestamp(3, Timestamp.from(created))
                statement.setDouble(4, size)
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    keys.next()
                    keys.getLong(1)
                }
            }

    private fun findOrCreateAuctionHouseId(
        connection: Connection,
        connectedId: Int,
    ): Int {
        findExistingAuctionHouseId(connection, connectedId)?.let { return it }

        val preferredId = findPreferredAuctionHouseId(connection, connectedId) ?: connectedId
        if (!auctionHouseExists(connection, preferredId)) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO auction_house (
                        id,
                        connected_id,
                        region,
                        auto_update,
                        avg_delay,
                        game_build,
                        highest_delay,
                        lowest_delay,
                        stats_last_modified,
                        update_attempts
                    ) VALUES (?, ?, 'Europe', b'0', 45, 0, 45, 45, 0, 0)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, preferredId)
                    statement.setInt(2, connectedId)
                    statement.executeUpdate()
                }
        }

        updateConnectedRealmAuctionHouseId(connection, connectedId, preferredId)
        return preferredId
    }

    private fun updateConnectedRealmAuctionHouseId(
        connection: Connection,
        connectedId: Int,
        auctionHouseId: Int,
    ) {
        connection
            .prepareStatement("UPDATE connected_realm SET auction_house_id = ? WHERE id = ?")
            .use { statement ->
                statement.setInt(1, auctionHouseId)
                statement.setInt(2, connectedId)
                statement.executeUpdate()
            }
    }

    private fun findPreferredAuctionHouseId(
        connection: Connection,
        connectedId: Int,
    ): Int? =
        connection
            .prepareStatement("SELECT auction_house_id FROM connected_realm WHERE id = ?")
            .use { statement ->
                statement.setInt(1, connectedId)
                statement.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
            }

    private fun findExistingAuctionHouseId(
        connection: Connection,
        connectedId: Int,
    ): Int? =
        connection
            .prepareStatement("SELECT id FROM auction_house WHERE connected_id = ?")
            .use { statement ->
                statement.setInt(1, connectedId)
                statement.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
            }

    private fun auctionHouseExists(
        connection: Connection,
        auctionHouseId: Int,
    ): Boolean =
        connection
            .prepareStatement("SELECT 1 FROM auction_house WHERE id = ?")
            .use { statement ->
                statement.setInt(1, auctionHouseId)
                statement.executeQuery().use(ResultSet::next)
            }

    private fun auctionHouseFileLogExists(
        connection: Connection,
        auctionHouseId: Int,
        lastModified: Instant,
    ): Boolean =
        connection
            .prepareStatement(
                "SELECT 1 FROM auction_house_file_log WHERE auction_house_id = ? AND last_modified = ?",
            ).use { statement ->
                statement.setInt(1, auctionHouseId)
                statement.setTimestamp(2, Timestamp.from(lastModified))
                statement.executeQuery().use(ResultSet::next)
            }

    private fun extractBucketName(path: String): String =
        path
            .substringAfter("://", "")
            .substringBefore('/')
            .ifBlank { "unknown" }

    private fun property(
        systemPropertyName: String,
        environmentVariableName: String,
    ): String =
        System.getProperty(systemPropertyName)
            ?: System.getenv(environmentVariableName)
            ?: applicationProperties.getProperty(systemPropertyName)
            ?: ""

    private fun loadApplicationProperties() =
        YamlPropertiesFactoryBean()
            .apply {
                setResources(
                    *buildList {
                        add(ClassPathResource("application.yml"))
                        addAll(activeProfileResources())
                        val testResource = ClassPathResource("application-test.yml")
                        if (testResource.exists()) {
                            add(testResource)
                        }
                    }.toTypedArray(),
                )
            }.`object` ?: java.util.Properties()

    private fun activeProfileResources() =
        (System.getProperty("spring.profiles.active") ?: System.getenv("SPRING_PROFILES_ACTIVE"))
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { ClassPathResource("application-$it.yml") }
            .filter(ClassPathResource::exists)
}
