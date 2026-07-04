package net.jonasmf.auctionengine.config

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.DriverManager

@Component
class BranchDatabaseCloner {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun prepareBranchDatabase(
        jdbcUrl: String,
        username: String?,
        password: String?,
        selectedDatabase: SelectedDatabase,
    ) {
        if (!selectedDatabase.shouldCloneFromTemplate) return

        DriverManager.getConnection(jdbcUrl.withoutDatabase(), username, password).use { connection ->
            connection.autoCommit = false
            try {
                connection.lockBranchDatabase(selectedDatabase.name)
                if (connection.databaseExists(selectedDatabase.name)) {
                    connection.commit()
                    logger.info("Using existing local dev database {}", selectedDatabase.name)
                    return
                }

                require(connection.databaseExists(selectedDatabase.cloneSourceDatabase)) {
                    "Template database '${selectedDatabase.cloneSourceDatabase}' does not exist"
                }

                logger.info(
                    "Creating local dev database {} from {}",
                    selectedDatabase.name,
                    selectedDatabase.cloneSourceDatabase,
                )
                connection.cloneDatabase(
                    sourceDatabase = selectedDatabase.cloneSourceDatabase,
                    targetDatabase = selectedDatabase.name,
                )
                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            }
        }
    }
}

private val SKIP_DATA_TABLES =
    setOf(
        "admin_job",
        "auction",
        "auction_house_file_log",
        "auction_update_history",
        "auction_price",
        "blizzard_media_fetch_failure",
        "connected_realm_update_history",
        "file_reference",
        "item_fetch_failure",
    )

private val BOUNDED_HISTORY_TABLES =
    setOf(
        "auction_stats_daily",
        "auction_stats_hourly",
    )

private val SEQUENCE_RESETS =
    mapOf(
        "auction_house_seq" to SequenceReset(tableName = "auction_house", columnName = "id"),
        "file_reference_seq" to SequenceReset(tableName = "file_reference", columnName = "id"),
    )

private data class DatabaseObject(
    val name: String,
    val type: DatabaseObjectType,
)

private enum class DatabaseObjectType {
    TABLE,
    SEQUENCE,
}

private data class SequenceReset(
    val tableName: String,
    val columnName: String,
)

internal fun String.withoutDatabase(): String {
    val prefix = "jdbc:mariadb://"
    if (!startsWith(prefix)) return this
    val queryIndex = indexOf('?')
    val query = if (queryIndex == -1) "" else substring(queryIndex)
    val withoutQuery = if (queryIndex == -1) this else substring(0, queryIndex)
    val pathIndex = withoutQuery.indexOf('/', prefix.length)
    return if (pathIndex == -1) {
        "$withoutQuery/$query"
    } else {
        withoutQuery.substring(0, pathIndex + 1) + query
    }
}

private fun Connection.lockBranchDatabase(databaseName: String) {
    prepareStatement("SELECT GET_LOCK(?, 60)").use { statement ->
        statement.setString(1, "wae.branch-database.$databaseName")
        statement.executeQuery().use { result ->
            require(result.next() && result.getInt(1) == 1) {
                "Timed out waiting for branch database clone lock for '$databaseName'"
            }
        }
    }
}

private fun Connection.databaseExists(database: String): Boolean =
    prepareStatement(
        "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
    ).use { statement ->
        statement.setString(1, database)
        statement.executeQuery().use { result ->
            result.next()
            result.getBoolean(1)
        }
    }

private fun Connection.cloneDatabase(
    sourceDatabase: String,
    targetDatabase: String,
) {
    executeSql(
        """
        CREATE DATABASE ${quoteIdentifier(targetDatabase)}
        CHARACTER SET utf8mb3
        COLLATE utf8mb3_general_ci
        """.trimIndent(),
    )
    executeSql("SET FOREIGN_KEY_CHECKS = 0")
    try {
        val objects = databaseObjects(sourceDatabase)
        cloneTablesAndSequences(sourceDatabase = sourceDatabase, targetDatabase = targetDatabase, objects = objects)
        resetCopiedSequences(targetDatabase = targetDatabase, sequences = objects.sequences())
        cloneViews(sourceDatabase = sourceDatabase, targetDatabase = targetDatabase)
    } finally {
        executeSql("SET FOREIGN_KEY_CHECKS = 1")
    }
}

private fun Connection.cloneTablesAndSequences(
    sourceDatabase: String,
    targetDatabase: String,
    objects: List<DatabaseObject>,
) {
    objects.forEach { databaseObject ->
        val tableName = databaseObject.name
        val createTableSql =
            showCreateTable(sourceDatabase, tableName)
                .qualifyCreateTableOrSequence(targetDatabase, tableName)
        executeSql(createTableSql)
        if (databaseObject.type == DatabaseObjectType.TABLE) {
            cloneData(
                sourceDatabase = sourceDatabase,
                targetDatabase = targetDatabase,
                tableName = tableName,
            )
        }
    }
}

private fun Connection.cloneData(
    sourceDatabase: String,
    targetDatabase: String,
    tableName: String,
) {
    dataCopySql(
        sourceDatabase = sourceDatabase,
        targetDatabase = targetDatabase,
        tableName = tableName,
    )?.let(::executeSql)
}

internal fun dataCopySql(
    sourceDatabase: String,
    targetDatabase: String,
    tableName: String,
): String? {
    if (tableName in SKIP_DATA_TABLES) return null

    val sql =
        """
        INSERT INTO ${quoteIdentifier(targetDatabase)}.${quoteIdentifier(tableName)}
        SELECT *
        FROM ${quoteIdentifier(sourceDatabase)}.${quoteIdentifier(tableName)}
        """.trimIndent()

    return if (tableName in BOUNDED_HISTORY_TABLES) {
        "$sql\nWHERE `date` >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)"
    } else {
        sql
    }
}

private fun Connection.resetCopiedSequences(
    targetDatabase: String,
    sequences: List<String>,
) {
    sequences
        .mapNotNull { sequenceName -> SEQUENCE_RESETS[sequenceName]?.let { sequenceName to it } }
        .forEach { (sequenceName, reset) ->
            val nextValue =
                maxValue(
                    database = targetDatabase,
                    tableName = reset.tableName,
                    columnName = reset.columnName,
                ) + 1
            executeSql("DO SETVAL(${qualifiedIdentifier(targetDatabase, sequenceName)}, $nextValue, 0)")
        }
}

private fun Connection.maxValue(
    database: String,
    tableName: String,
    columnName: String,
): Long =
    createStatement().use { statement ->
        statement
            .executeQuery(
                """
                SELECT COALESCE(MAX(${quoteIdentifier(columnName)}), 0)
                FROM ${qualifiedIdentifier(database, tableName)}
                """.trimIndent(),
            ).use { result ->
                result.next()
                result.getLong(1)
            }
    }

private fun Connection.cloneViews(
    sourceDatabase: String,
    targetDatabase: String,
) {
    viewNames(sourceDatabase).forEach { viewName ->
        val createViewSql =
            showCreateView(sourceDatabase, viewName)
                .replaceFirst(
                    Regex("""(?i)^CREATE(?:\s+ALGORITHM=\S+)?\s+DEFINER=`[^`]+`@`[^`]+`\s+SQL SECURITY \S+\s+VIEW\s+`${Regex.escape(viewName)}`"""),
                    "CREATE VIEW ${quoteIdentifier(targetDatabase)}.${quoteIdentifier(viewName)}",
                ).replace(
                    Regex("""`${Regex.escape(sourceDatabase)}`\."""),
                    "${quoteIdentifier(targetDatabase)}.",
                )
        executeSql(createViewSql)
    }
}

private fun Connection.databaseObjects(database: String): List<DatabaseObject> =
    prepareStatement(
        """
        SELECT table_name, table_type
        FROM information_schema.tables
        WHERE table_schema = ?
          AND table_type IN ('BASE TABLE', 'SEQUENCE')
        ORDER BY table_name
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, database)
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(
                        DatabaseObject(
                            name = result.getString("table_name"),
                            type =
                                when (result.getString("table_type")) {
                                    "SEQUENCE" -> DatabaseObjectType.SEQUENCE
                                    else -> DatabaseObjectType.TABLE
                                },
                        ),
                    )
                }
            }
        }
    }

private fun Connection.viewNames(database: String): List<String> =
    prepareStatement(
        """
        SELECT table_name
        FROM information_schema.views
        WHERE table_schema = ?
        ORDER BY table_name
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, database)
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) add(result.getString("table_name"))
            }
        }
    }

private fun Connection.showCreateTable(
    database: String,
    tableName: String,
): String =
    createStatement().use { statement ->
        statement.executeQuery("SHOW CREATE TABLE ${quoteIdentifier(database)}.${quoteIdentifier(tableName)}").use { result ->
            result.next()
            result.getString(2)
        }
    }

private fun Connection.showCreateView(
    database: String,
    viewName: String,
): String =
    createStatement().use { statement ->
        statement.executeQuery("SHOW CREATE VIEW ${quoteIdentifier(database)}.${quoteIdentifier(viewName)}").use { result ->
            result.next()
            result.getString(2)
        }
    }

internal fun String.qualifyCreateTableOrSequence(
    database: String,
    objectName: String,
): String =
    replaceFirst(
        Regex("""(?i)^CREATE\s+(TABLE|SEQUENCE)\s+`${Regex.escape(objectName)}`"""),
        "CREATE $1 ${qualifiedIdentifier(database, objectName)}",
    )

private fun List<DatabaseObject>.sequences(): List<String> =
    filter { it.type == DatabaseObjectType.SEQUENCE }.map { it.name }

private fun Connection.executeSql(sql: String) {
    createStatement().use { statement -> statement.execute(sql) }
}

private fun qualifiedIdentifier(
    database: String,
    value: String,
): String = "${quoteIdentifier(database)}.${quoteIdentifier(value)}"

private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
