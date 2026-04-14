package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection

@Suppress("ClassName")
class V4__align_hourly_auction_stats_indexes_and_storage : BaseJavaMigration() {
    override fun canExecuteInTransaction(): Boolean = false

    override fun migrate(context: Context) {
        val connection = context.connection
        val schema = queryCurrentSchema(connection) ?: return
        if (!tableExists(connection, schema, TABLE_NAME)) return

        val indexes = loadIndexes(connection, schema, TABLE_NAME)
        val storageAligned = isStorageAligned(showCreateTable(connection, TABLE_NAME))
        val indexesAligned = areIndexesAligned(indexes)

        if (storageAligned) {
            if (!indexesAligned) {
                normalizeIndexes(connection, schema, TABLE_NAME)
            }
            return
        }

        rebuildHourlyAuctionStatsTable(connection, schema, loadViewDefinition(connection, schema, VIEW_NAME))
    }

    private fun rebuildHourlyAuctionStatsTable(
        connection: Connection,
        schema: String,
        viewDefinition: String?,
    ) {
        execute(connection, "DROP VIEW IF EXISTS `$VIEW_NAME`")
        execute(connection, "DROP TABLE IF EXISTS `$TEMP_TABLE_NAME`")
        execute(connection, "DROP TABLE IF EXISTS `$BACKUP_TABLE_NAME`")
        execute(connection, "CREATE TABLE `$TEMP_TABLE_NAME` LIKE `$TABLE_NAME`")

        dropSecondaryIndexes(connection, schema, TEMP_TABLE_NAME)
        execute(
            connection,
            """
            ALTER TABLE `$TEMP_TABLE_NAME`
                ENGINE=InnoDB,
                MAX_ROWS=$TARGET_MAX_ROWS
            """.trimIndent(),
        )
        execute(
            connection,
            """
            ALTER TABLE `$TEMP_TABLE_NAME`
                CONVERT TO CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci
            """.trimIndent(),
        )
        execute(
            connection,
            """
            ALTER TABLE `$TEMP_TABLE_NAME`
                PARTITION BY HASH (to_days(`date`))
                PARTITIONS $TARGET_PARTITIONS
            """.trimIndent(),
        )
        execute(connection, "INSERT INTO `$TEMP_TABLE_NAME` SELECT * FROM `$TABLE_NAME`")
        execute(
            connection,
            """
            RENAME TABLE
                `$TABLE_NAME` TO `$BACKUP_TABLE_NAME`,
                `$TEMP_TABLE_NAME` TO `$TABLE_NAME`
            """.trimIndent(),
        )
        normalizeIndexes(connection, schema, TABLE_NAME)
        execute(connection, "DROP TABLE `$BACKUP_TABLE_NAME`")
        recreateAuctionHousePricesView(connection, viewDefinition)
    }

    private fun normalizeIndexes(
        connection: Connection,
        schema: String,
        tableName: String,
    ) {
        val indexes = loadIndexes(connection, schema, tableName)

        dropEquivalentIndexes(
            connection = connection,
            indexes = indexes,
            tableName = tableName,
            expectedName = REALM_DATE_INDEX,
            expectedColumns = REALM_DATE_COLUMNS,
        )
        dropEquivalentIndexes(
            connection = connection,
            indexes = indexes,
            tableName = tableName,
            expectedName = REALM_ITEM_DATE_INDEX,
            expectedColumns = REALM_ITEM_DATE_COLUMNS,
        )

        val refreshedIndexes = loadIndexes(connection, schema, tableName)

        if (refreshedIndexes.containsKey(REALM_DATE_INDEX) &&
            refreshedIndexes[REALM_DATE_INDEX] != REALM_DATE_COLUMNS
        ) {
            execute(
                connection,
                """
                ALTER TABLE `$tableName`
                    DROP INDEX `$REALM_DATE_INDEX`
                """.trimIndent(),
            )
        }

        if (refreshedIndexes.containsKey(REALM_ITEM_DATE_INDEX) &&
            refreshedIndexes[REALM_ITEM_DATE_INDEX] != REALM_ITEM_DATE_COLUMNS
        ) {
            execute(
                connection,
                """
                ALTER TABLE `$tableName`
                    DROP INDEX `$REALM_ITEM_DATE_INDEX`
                """.trimIndent(),
            )
        }

        val finalIndexes = loadIndexes(connection, schema, tableName)

        if (finalIndexes[REALM_DATE_INDEX] != REALM_DATE_COLUMNS) {
            execute(
                connection,
                """
                ALTER TABLE `$tableName`
                    ADD INDEX `$REALM_DATE_INDEX` (`connected_realm_id`, `date`)
                """.trimIndent(),
            )
        }

        if (finalIndexes[REALM_ITEM_DATE_INDEX] != REALM_ITEM_DATE_COLUMNS) {
            execute(
                connection,
                """
                ALTER TABLE `$tableName`
                    ADD INDEX `$REALM_ITEM_DATE_INDEX` (`connected_realm_id`, `item_id`, `date`)
                """.trimIndent(),
            )
        }
    }

    private fun dropEquivalentIndexes(
        connection: Connection,
        indexes: Map<String, List<String>>,
        tableName: String,
        expectedName: String,
        expectedColumns: List<String>,
    ) {
        indexes
            .filterKeys { it != "PRIMARY" }
            .filter { (name, columns) -> name != expectedName && columns == expectedColumns }
            .keys
            .forEach { indexName ->
                execute(
                    connection,
                    """
                    ALTER TABLE `$tableName`
                        DROP INDEX `$indexName`
                    """.trimIndent(),
                )
            }
    }

    private fun dropSecondaryIndexes(
        connection: Connection,
        schema: String,
        tableName: String,
    ) {
        loadIndexes(connection, schema, tableName)
            .keys
            .filter { it != "PRIMARY" }
            .forEach { indexName ->
                execute(
                    connection,
                    """
                    ALTER TABLE `$tableName`
                        DROP INDEX `$indexName`
                    """.trimIndent(),
                )
            }
    }

    private fun areIndexesAligned(indexes: Map<String, List<String>>): Boolean {
        val namedIndexesAligned =
            indexes[REALM_DATE_INDEX] == REALM_DATE_COLUMNS &&
                indexes[REALM_ITEM_DATE_INDEX] == REALM_ITEM_DATE_COLUMNS

        val unexpectedEquivalentIndexes =
            indexes
                .filterKeys { it != "PRIMARY" && it != REALM_DATE_INDEX && it != REALM_ITEM_DATE_INDEX }
                .values
                .any { columns -> columns == REALM_DATE_COLUMNS || columns == REALM_ITEM_DATE_COLUMNS }

        return namedIndexesAligned && !unexpectedEquivalentIndexes
    }

    private fun loadIndexes(
        connection: Connection,
        schema: String,
        tableName: String,
    ): Map<String, List<String>> {
        connection
            .prepareStatement(
                """
                SELECT index_name, column_name
                FROM information_schema.statistics
                WHERE table_schema = ?
                  AND table_name = ?
                ORDER BY index_name, seq_in_index
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, schema)
                statement.setString(2, tableName)

                statement.executeQuery().use { rs ->
                    val indexes = linkedMapOf<String, MutableList<String>>()
                    while (rs.next()) {
                        val indexName = rs.getString("index_name")
                        val columnName = rs.getString("column_name")
                        indexes.getOrPut(indexName) { mutableListOf() }.add(columnName)
                    }
                    return indexes.mapValues { (_, columns) -> columns.toList() }
                }
            }
    }

    private fun tableExists(
        connection: Connection,
        schema: String,
        tableName: String,
    ): Boolean =
        connection
            .prepareStatement(
                """
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, schema)
                statement.setString(2, tableName)
                statement.executeQuery().use { rs -> rs.next() }
            }

    private fun showCreateTable(
        connection: Connection,
        tableName: String,
    ): String? =
        connection
            .createStatement()
            .use { statement ->
                statement.executeQuery("SHOW CREATE TABLE `$tableName`").use { rs ->
                    if (!rs.next()) return null
                    rs.getString("Create Table")
                }
            }

    private fun queryCurrentSchema(connection: Connection): String? =
        connection
            .createStatement()
            .use { statement ->
                statement.executeQuery("SELECT DATABASE()").use { rs ->
                    if (!rs.next()) return null
                    rs.getString(1)
                }
            }

    private fun loadViewDefinition(
        connection: Connection,
        schema: String,
        viewName: String,
    ): String? =
        connection
            .prepareStatement(
                """
                SELECT view_definition
                FROM information_schema.views
                WHERE table_schema = ?
                  AND table_name = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, schema)
                statement.setString(2, viewName)
                statement.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    "CREATE OR REPLACE VIEW `$viewName` AS ${rs.getString("view_definition")}"
                }
            }

    private fun recreateAuctionHousePricesView(
        connection: Connection,
        viewDefinition: String?,
    ) {
        if (viewDefinition != null) {
            execute(connection, viewDefinition)
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

    private fun isStorageAligned(createTableSql: String?): Boolean {
        if (createTableSql.isNullOrBlank()) return false
        val normalized = createTableSql.lowercase().replace("`", "").replace(Regex("\\s+"), " ")
        return normalized.contains("engine=innodb") &&
            normalized.contains("default charset=utf8mb3") &&
            normalized.contains("collate=utf8mb3_general_ci") &&
            normalized.contains("max_rows=$TARGET_MAX_ROWS") &&
            normalized.contains("partition by hash (to_days(date))") &&
            normalized.contains("partitions $TARGET_PARTITIONS")
    }

    companion object {
        private const val TABLE_NAME = "hourly_auction_stats"
        private const val TEMP_TABLE_NAME = "hourly_auction_stats_v4_new"
        private const val BACKUP_TABLE_NAME = "hourly_auction_stats_v4_old"
        private const val VIEW_NAME = "v_auction_house_prices"
        private const val TARGET_MAX_ROWS = 82300000
        private const val TARGET_PARTITIONS = 31
        private const val REALM_DATE_INDEX = "idx_hourly_auction_stats_connected_realm_id_date"
        private const val REALM_ITEM_DATE_INDEX = "idx_hourly_auction_stats_connected_realm_id_item_id_date"
        private val REALM_DATE_COLUMNS = listOf("connected_realm_id", "date")
        private val REALM_ITEM_DATE_COLUMNS = listOf("connected_realm_id", "item_id", "date")
    }
}
