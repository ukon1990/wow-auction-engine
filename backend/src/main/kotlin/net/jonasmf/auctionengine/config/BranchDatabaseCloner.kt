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

private val BOUNDED_HISTORY_TABLES =
    setOf(
        "auction_stats_daily",
        "auction_stats_hourly",
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
        cloneTables(sourceDatabase = sourceDatabase, targetDatabase = targetDatabase)
        cloneViews(sourceDatabase = sourceDatabase, targetDatabase = targetDatabase)
    } finally {
        executeSql("SET FOREIGN_KEY_CHECKS = 1")
    }
}

private fun Connection.cloneTables(
    sourceDatabase: String,
    targetDatabase: String,
) {
    tableNames(sourceDatabase).forEach { tableName ->
        val createTableSql =
            showCreateTable(sourceDatabase, tableName)
                .replaceFirst(
                    Regex("""(?i)^CREATE TABLE\s+`${Regex.escape(tableName)}`"""),
                    "CREATE TABLE ${quoteIdentifier(targetDatabase)}.${quoteIdentifier(tableName)}",
                )
        executeSql(createTableSql)
        cloneData(
            sourceDatabase = sourceDatabase,
            targetDatabase = targetDatabase,
            tableName = tableName,
        )
    }
}

private fun Connection.cloneData(
    sourceDatabase: String,
    targetDatabase: String,
    tableName: String,
) {
    val datePredicate =
        if (tableName in BOUNDED_HISTORY_TABLES) {
            " WHERE `date` >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)"
        } else {
            ""
        }
    executeSql(
        """
        INSERT INTO ${quoteIdentifier(targetDatabase)}.${quoteIdentifier(tableName)}
        SELECT *
        FROM ${quoteIdentifier(sourceDatabase)}.${quoteIdentifier(tableName)}
        $datePredicate
        """.trimIndent(),
    )
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

private fun Connection.tableNames(database: String): List<String> =
    prepareStatement(
        """
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = ?
          AND table_type = 'BASE TABLE'
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

private fun Connection.executeSql(sql: String) {
    createStatement().use { statement -> statement.execute(sql) }
}

private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
