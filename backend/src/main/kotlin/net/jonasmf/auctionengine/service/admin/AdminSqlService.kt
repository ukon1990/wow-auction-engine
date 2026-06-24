package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.AdminSqlExecuteRequest
import net.jonasmf.auctionengine.generated.model.AdminSqlColumn
import net.jonasmf.auctionengine.generated.model.AdminSqlIndex
import net.jonasmf.auctionengine.generated.model.AdminSqlMetadata
import net.jonasmf.auctionengine.generated.model.AdminSqlResult
import net.jonasmf.auctionengine.generated.model.AdminSqlTable
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import kotlin.time.measureTimedValue

@Service
class AdminSqlService(
    private val jdbcTemplate: JdbcTemplate,
) {
    private companion object {
        const val DEFAULT_ROW_LIMIT = 500
        const val MAX_ROW_LIMIT = 500
        const val QUERY_TIMEOUT_SECONDS = 30

        val ALLOWED_QUERY_START = setOf("SELECT", "WITH", "SHOW", "DESCRIBE", "DESC", "EXPLAIN")
        val EXPLAINABLE_QUERY_START = setOf("SELECT", "WITH")
        val BLOCKED_KEYWORDS =
            setOf(
                "INSERT",
                "UPDATE",
                "DELETE",
                "REPLACE",
                "CREATE",
                "ALTER",
                "DROP",
                "TRUNCATE",
                "RENAME",
                "GRANT",
                "REVOKE",
                "CALL",
                "DO",
                "SET",
                "LOCK",
                "UNLOCK",
                "LOAD",
                "KILL",
                "USE",
                "START",
                "COMMIT",
                "ROLLBACK",
                "OPTIMIZE",
                "REPAIR",
            )
    }

    fun execute(request: AdminSqlExecuteRequest): AdminSqlResult {
        val sql = request.sql.trim()
        if (sql.isBlank()) {
            throw badRequest("SQL is required")
        }

        val normalized = normalizeSql(sql)
        val mode = request.mode
        val limit = if (request.limitRows) (request.rowLimit ?: DEFAULT_ROW_LIMIT).coerceIn(1, MAX_ROW_LIMIT) else null
        val effectiveSql =
            when (mode) {
                AdminSqlExecuteRequest.Mode.QUERY -> validateReadOnlySql(sql, normalized, limit)
                AdminSqlExecuteRequest.Mode.EXPLAIN -> "EXPLAIN FORMAT=JSON ${validateExplainableSql(sql, normalized, limit)}"
                AdminSqlExecuteRequest.Mode.ANALYZE -> "ANALYZE FORMAT=JSON ${validateExplainableSql(sql, normalized, limit)}"
            }

        return runSql(mode.toResultMode(), effectiveSql, limit ?: MAX_ROW_LIMIT)
    }

    fun getMetadata(): AdminSqlMetadata {
        val tables =
            jdbcTemplate.query(
                """
                SELECT TABLE_NAME AS name,
                       ENGINE AS engine,
                       TABLE_ROWS AS tableRows
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                ORDER BY TABLE_NAME
                """.trimIndent(),
            ) { rs, _ ->
                AdminSqlTable(
                    name = rs.getString("name"),
                    engine = rs.getString("engine"),
                    tableRows = rs.getNullableLong("tableRows"),
                    columns = emptyList(),
                    indexes = emptyList(),
                )
            }
        val columnsByTable = getColumns().groupBy { it.tableName }
        val indexesByTable = getIndexes().groupBy { it.tableName }

        return AdminSqlMetadata(
            tables =
                tables.map { table ->
                    table.copy(
                        columns = columnsByTable[table.name]?.map { it.column } ?: emptyList(),
                        indexes = indexesByTable[table.name]?.map { it.index } ?: emptyList(),
                    )
                },
        )
    }

    private fun getColumns(): List<ColumnMetadata> =
        jdbcTemplate.query(
            """
            SELECT TABLE_NAME AS tableName,
                   COLUMN_NAME AS name,
                   DATA_TYPE AS dataType,
                   COLUMN_TYPE AS columnType,
                   IS_NULLABLE AS nullable,
                   COLUMN_DEFAULT AS defaultValue,
                   EXTRA AS extra,
                   ORDINAL_POSITION AS ordinalPosition
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            ORDER BY TABLE_NAME, ORDINAL_POSITION
            """.trimIndent(),
        ) { rs, _ ->
            ColumnMetadata(
                tableName = rs.getString("tableName"),
                column =
                    AdminSqlColumn(
                        name = rs.getString("name"),
                        dataType = rs.getString("dataType"),
                        columnType = rs.getString("columnType"),
                        nullable = rs.getString("nullable").equals("YES", ignoreCase = true),
                        defaultValue = rs.getString("defaultValue"),
                        extra = rs.getString("extra"),
                        ordinalPosition = rs.getInt("ordinalPosition"),
                    ),
            )
        }

    private fun getIndexes(): List<IndexMetadata> {
        val rows =
            jdbcTemplate.query(
                """
                SELECT TABLE_NAME AS tableName,
                       INDEX_NAME AS name,
                       NON_UNIQUE AS nonUnique,
                       COLUMN_NAME AS columnName,
                       SEQ_IN_INDEX AS sequenceInIndex
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """.trimIndent(),
            ) { rs, _ ->
                IndexColumnMetadata(
                    tableName = rs.getString("tableName"),
                    indexName = rs.getString("name"),
                    unique = rs.getInt("nonUnique") == 0,
                    columnName = rs.getString("columnName"),
                    sequenceInIndex = rs.getInt("sequenceInIndex"),
                )
            }

        return rows
            .groupBy { it.tableName to it.indexName }
            .map { (key, values) ->
                val sortedValues = values.sortedBy { it.sequenceInIndex }
                IndexMetadata(
                    tableName = key.first,
                    index =
                        AdminSqlIndex(
                            name = key.second,
                            unique = sortedValues.firstOrNull()?.unique ?: false,
                            columns = sortedValues.map { it.columnName },
                        ),
                )
            }.sortedWith(compareBy<IndexMetadata> { it.tableName }.thenBy { it.index.name })
    }

    private fun validateReadOnlySql(
        sql: String,
        normalized: NormalizedSql,
        limit: Int?,
    ): String {
        validateCommon(normalized)
        val firstKeyword = normalized.firstKeyword()
        if (firstKeyword !in ALLOWED_QUERY_START) {
            throw badRequest("Only read-only SQL diagnostics are allowed")
        }
        if (firstKeyword == "ANALYZE" && normalized.secondKeyword() == "TABLE") {
            throw badRequest("ANALYZE TABLE is not allowed")
        }
        return applyLimit(sql, normalized, limit)
    }

    private fun validateExplainableSql(
        sql: String,
        normalized: NormalizedSql,
        limit: Int?,
    ): String {
        validateCommon(normalized)
        if (normalized.firstKeyword() !in EXPLAINABLE_QUERY_START) {
            throw badRequest("Explain and analyze can only run SELECT or WITH queries")
        }
        return applyLimit(sql, normalized, limit)
    }

    private fun validateCommon(normalized: NormalizedSql) {
        if (normalized.sqlWithoutLiterals.contains(';')) {
            throw badRequest("Only one SQL statement is allowed")
        }

        val blockedKeyword = normalized.keywords.firstOrNull { keyword -> keyword in BLOCKED_KEYWORDS }
        if (blockedKeyword != null) {
            throw badRequest("$blockedKeyword statements are not allowed")
        }
    }

    private fun applyLimit(
        sql: String,
        normalized: NormalizedSql,
        limit: Int?,
    ): String {
        if (limit == null || normalized.firstKeyword() !in EXPLAINABLE_QUERY_START) {
            return sql
        }

        val topLevelLimit = normalized.topLevelLimit()
        if (topLevelLimit == null) {
            return "$sql LIMIT $limit"
        }
        if (topLevelLimit.value > limit) {
            throw badRequest("LIMIT must be $limit or lower while result limiting is enabled")
        }
        return sql
    }

    private fun runSql(
        mode: AdminSqlResult.Mode,
        effectiveSql: String,
        rowLimit: Int,
    ): AdminSqlResult {
        try {
            val timed =
                measureTimedValue {
                    jdbcTemplate.query(
                        { connection ->
                            connection.prepareStatement(effectiveSql).also { statement ->
                                statement.queryTimeout = QUERY_TIMEOUT_SECONDS
                                statement.maxRows = rowLimit + 1
                            }
                        },
                        ResultSetExtractor { rs ->
                            val metadata = rs.metaData
                            val columns =
                                (1..metadata.columnCount).map { index ->
                                    metadata.getColumnLabel(index).takeIf { it.isNotBlank() }
                                        ?: metadata.getColumnName(index)
                                }
                            val rows = mutableListOf<List<String?>>()
                            while (rs.next() && rows.size <= rowLimit) {
                                rows +=
                                    (1..metadata.columnCount).map { index ->
                                        rs.getObject(index)?.toString()
                                    }
                            }
                            SqlRows(columns = columns, rows = rows)
                        },
                    )
                }
            val rows = timed.value.rows.take(rowLimit)
            return AdminSqlResult(
                mode = mode,
                effectiveSql = effectiveSql,
                columns = timed.value.columns,
                rows = rows,
                rowCount = rows.size,
                truncated = timed.value.rows.size > rowLimit,
                durationMs = timed.duration.inWholeMilliseconds,
            )
        } catch (error: DataAccessException) {
            throw badRequest(error.mostSpecificCause.message ?: "SQL execution failed")
        }
    }

    private fun AdminSqlExecuteRequest.Mode.toResultMode(): AdminSqlResult.Mode =
        when (this) {
            AdminSqlExecuteRequest.Mode.QUERY -> AdminSqlResult.Mode.QUERY
            AdminSqlExecuteRequest.Mode.EXPLAIN -> AdminSqlResult.Mode.EXPLAIN
            AdminSqlExecuteRequest.Mode.ANALYZE -> AdminSqlResult.Mode.ANALYZE
        }

    private fun badRequest(message: String): ResponseStatusException =
        ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}

private data class SqlRows(
    val columns: List<String>,
    val rows: List<List<String?>>,
)

private data class ColumnMetadata(
    val tableName: String,
    val column: AdminSqlColumn,
)

private data class IndexColumnMetadata(
    val tableName: String,
    val indexName: String,
    val unique: Boolean,
    val columnName: String,
    val sequenceInIndex: Int,
)

private data class IndexMetadata(
    val tableName: String,
    val index: AdminSqlIndex,
)

private data class NormalizedSql(
    val sqlWithoutLiterals: String,
    val keywords: List<String>,
) {
    fun firstKeyword(): String? = keywords.firstOrNull()

    fun secondKeyword(): String? = keywords.drop(1).firstOrNull()

    fun topLevelLimit(): TopLevelLimit? {
        var depth = 0
        var index = 0
        while (index < sqlWithoutLiterals.length) {
            when (sqlWithoutLiterals[index]) {
                '(' -> depth += 1
                ')' -> depth = (depth - 1).coerceAtLeast(0)
            }
            if (depth == 0 && sqlWithoutLiterals.regionMatches(index, "LIMIT", 0, 5, ignoreCase = true)) {
                val before = sqlWithoutLiterals.getOrNull(index - 1)
                val after = sqlWithoutLiterals.getOrNull(index + 5)
                if (!before.isIdentifierPart() && !after.isIdentifierPart()) {
                    val value =
                        Regex("""\G\s+(\d+)""")
                            .find(sqlWithoutLiterals, index + 5)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                    if (value != null) {
                        return TopLevelLimit(value)
                    }
                }
            }
            index += 1
        }
        return null
    }
}

private data class TopLevelLimit(
    val value: Int,
)

private fun normalizeSql(sql: String): NormalizedSql {
    val cleaned = StringBuilder(sql.length)
    var index = 0
    while (index < sql.length) {
        val current = sql[index]
        val next = sql.getOrNull(index + 1)
        when {
            current == '\'' || current == '"' || current == '`' -> {
                val quote = current
                cleaned.append(' ')
                index += 1
                while (index < sql.length) {
                    if (sql[index] == '\\') {
                        index += 2
                        continue
                    }
                    if (sql[index] == quote) {
                        if (sql.getOrNull(index + 1) == quote) {
                            index += 2
                            continue
                        }
                        index += 1
                        break
                    }
                    index += 1
                }
                cleaned.append(' ')
            }
            current == '-' && next == '-' -> {
                cleaned.append(' ')
                index += 2
                while (index < sql.length && sql[index] != '\n') {
                    index += 1
                }
            }
            current == '#' -> {
                cleaned.append(' ')
                index += 1
                while (index < sql.length && sql[index] != '\n') {
                    index += 1
                }
            }
            current == '/' && next == '*' -> {
                cleaned.append(' ')
                index += 2
                while (index + 1 < sql.length && !(sql[index] == '*' && sql[index + 1] == '/')) {
                    index += 1
                }
                index = (index + 2).coerceAtMost(sql.length)
            }
            else -> {
                cleaned.append(current)
                index += 1
            }
        }
    }

    val withoutLiterals = cleaned.toString()
    return NormalizedSql(
        sqlWithoutLiterals = withoutLiterals,
        keywords =
            Regex("""[A-Za-z_][A-Za-z0-9_]*""")
                .findAll(withoutLiterals)
                .map { it.value.uppercase() }
                .toList(),
    )
}

private fun Char?.isIdentifierPart(): Boolean = this != null && (isLetterOrDigit() || this == '_')

private fun java.sql.ResultSet.getNullableLong(columnLabel: String): Long? {
    val value = getLong(columnLabel)
    return if (wasNull()) null else value
}
