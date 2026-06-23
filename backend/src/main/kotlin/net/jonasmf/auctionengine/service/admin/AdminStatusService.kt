package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.AdminConnectionStatus
import net.jonasmf.auctionengine.generated.model.AdminRunningQuery
import net.jonasmf.auctionengine.generated.model.AdminServerStatus
import net.jonasmf.auctionengine.generated.model.AdminStatus
import net.jonasmf.auctionengine.generated.model.AdminTableSize
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.lang.management.ManagementFactory

@Service
class AdminStatusService(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun getStatus(): AdminStatus =
        AdminStatus(
            connections = getConnectionStatus(),
            server = getServerStatus(),
            runningQueries = getRunningQueries(),
            tableSizes = getTableSizes(),
        )

    fun getConnectionStatus(): AdminConnectionStatus {
        val rows =
            jdbcTemplate.queryForList(
                """
                SHOW GLOBAL STATUS
                WHERE Variable_name IN (
                  'Max_used_connections',
                  'Threads_connected',
                  'Uptime'
                )
                """.trimIndent(),
            )
        val values =
            rows.associate { row ->
                row["Variable_name"].toString() to row["Value"].toLongValue()
            }

        return AdminConnectionStatus(
            maxUsedConnections = values["Max_used_connections"] ?: 0,
            threadsConnected = values["Threads_connected"] ?: 0,
            uptimeSeconds = values["Uptime"] ?: 0,
        )
    }

    fun getRunningQueries(): List<AdminRunningQuery> =
        jdbcTemplate.query(
            """
            SELECT  id,
                    query_id                         as queryId,
                    tid,
                    command,
                    state,
                    time,
                    time_ms                          as timeMs,
                    info,
                    stage,
                    max_stage                        as maxStage,
                    progress,
                    ROUND(memory_used / 1024 / 1024) as memoryUsed,
                    examined_rows                    as examinedRows
            FROM information_schema.processlist
            WHERE info IS NOT NULL
            AND info NOT LIKE '%SHOW GLOBAL STATUS%'
            AND info NOT LIKE '%information_schema.processlist%'
            """.trimIndent(),
        ) { rs, _ ->
            AdminRunningQuery(
                id = rs.getLong("id"),
                queryId = rs.getLong("queryId"),
                tid = rs.getLong("tid"),
                command = rs.getString("command"),
                state = rs.getString("state"),
                time = rs.getLong("time"),
                timeMs = rs.getDouble("timeMs"),
                info = rs.getString("info"),
                stage = rs.getNullableLong("stage"),
                maxStage = rs.getNullableLong("maxStage"),
                progress = rs.getNullableDouble("progress"),
                memoryUsed = rs.getNullableLong("memoryUsed"),
                examinedRows = rs.getNullableLong("examinedRows"),
            )
        }

    fun getTableSizes(): List<AdminTableSize> =
        jdbcTemplate.query(
            """
            SELECT TABLE_NAME AS name,
                   TABLE_ROWS AS tableRows,
                   ROUND((DATA_LENGTH) / 1024 / 1024) AS tableSizeInMb,
                   ROUND((INDEX_LENGTH) / 1024 / 1024) AS indexSizeInMb,
                   ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024) AS sizeInMb,
                   ROUND((DATA_FREE) / 1024 / 1024) AS freeTableSizeInMb,
                   ROUND((DATA_FREE + DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024) AS allocatedTableSize
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
               OR TABLE_SCHEMA LIKE '%wow%'
               OR TABLE_SCHEMA = 'dbo'
            ORDER BY (DATA_LENGTH + INDEX_LENGTH) DESC
            """.trimIndent(),
        ) { rs, _ ->
            AdminTableSize(
                name = rs.getString("name"),
                rows = rs.getLong("tableRows"),
                tableSizeInMb = rs.getLong("tableSizeInMb"),
                indexSizeInMb = rs.getLong("indexSizeInMb"),
                sizeInMb = rs.getLong("sizeInMb"),
                freeTableSizeInMb = rs.getLong("freeTableSizeInMb"),
                allocatedTableSize = rs.getLong("allocatedTableSize"),
            )
        }

    private fun getServerStatus(): AdminServerStatus {
        val runtime = Runtime.getRuntime()
        val megabyte = 1024L * 1024L
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        val extendedOsBean = osBean as? com.sun.management.OperatingSystemMXBean

        return AdminServerStatus(
            usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / megabyte,
            totalMemoryMb = runtime.totalMemory() / megabyte,
            freeMemoryMb = runtime.freeMemory() / megabyte,
            maxMemoryMb = runtime.maxMemory() / megabyte,
            processCpuLoad = extendedOsBean?.processCpuLoad?.toPercentOrNull(),
            systemCpuLoad = extendedOsBean?.cpuLoad?.toPercentOrNull(),
        )
    }
}

private fun java.sql.ResultSet.getNullableLong(columnLabel: String): Long? {
    val value = getLong(columnLabel)
    return if (wasNull()) null else value
}

private fun java.sql.ResultSet.getNullableDouble(columnLabel: String): Double? {
    val value = getDouble(columnLabel)
    return if (wasNull()) null else value
}

private fun Any?.toLongValue(): Long =
    when (this) {
        null -> 0
        is Number -> toLong()
        else -> toString().toLongOrNull() ?: 0
    }

private fun Double.toPercentOrNull(): Double? =
    takeIf { it >= 0.0 }?.let { it * 100.0 }
