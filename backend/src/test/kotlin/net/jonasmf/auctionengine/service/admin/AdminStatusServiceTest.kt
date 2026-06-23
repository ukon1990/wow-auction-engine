package net.jonasmf.auctionengine.service.admin

import io.mockk.every
import io.mockk.mockk
import net.jonasmf.auctionengine.generated.model.AdminRunningQuery
import net.jonasmf.auctionengine.generated.model.AdminTableSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AdminStatusServiceTest {
    private val jdbcTemplate = mockk<JdbcTemplate>()
    private val service = AdminStatusService(jdbcTemplate)

    @Test
    fun `maps connection status rows`() {
        every { jdbcTemplate.queryForList(match { it.contains("SHOW GLOBAL STATUS") }) } returns
            listOf(
                mapOf("Variable_name" to "Max_used_connections", "Value" to "12"),
                mapOf("Variable_name" to "Threads_connected", "Value" to "3"),
                mapOf("Variable_name" to "Uptime", "Value" to "456"),
            )

        val status = service.getConnectionStatus()

        assertEquals(12, status.maxUsedConnections)
        assertEquals(3, status.threadsConnected)
        assertEquals(456, status.uptimeSeconds)
    }

    @Test
    fun `maps running query rows`() {
        val resultSet = mockk<ResultSet>()
        every { resultSet.getLong("id") } returns 1
        every { resultSet.getLong("queryId") } returns 2
        every { resultSet.getLong("tid") } returns 3
        every { resultSet.getString("command") } returns "Query"
        every { resultSet.getString("state") } returns "Sending data"
        every { resultSet.getLong("time") } returns 4
        every { resultSet.getDouble("timeMs") } returns 4100.0
        every { resultSet.getTimestamp("startedAt") } returns Timestamp.from(Instant.parse("2026-06-23T06:00:00Z"))
        every { resultSet.getString("info") } returns "SELECT * FROM auction"
        every { resultSet.getLong("stage") } returns 1
        every { resultSet.getLong("maxStage") } returns 2
        every { resultSet.getDouble("progress") } returns 50.5
        every { resultSet.getLong("memoryUsed") } returns 10
        every { resultSet.getLong("examinedRows") } returns 20
        every { resultSet.wasNull() } returns false
        every {
            jdbcTemplate.query(match<String> { it.contains("information_schema.processlist") }, any<RowMapper<AdminRunningQuery>>())
        } answers {
            val sql = firstArg<String>()
            assert(sql.contains("LEFT(info, 10000)"))
            assert(sql.contains("LIMIT 100"))
            listOf(secondArg<RowMapper<AdminRunningQuery>>().mapRow(resultSet, 0))
        }

        val query = service.getRunningQueries().single()

        assertEquals(1, query.id)
        assertEquals(2, query.queryId)
        assertEquals(3, query.tid)
        assertEquals("Query", query.command)
        assertEquals("Sending data", query.state)
        assertEquals(4, query.time)
        assertEquals(4100.0, query.timeMs)
        assertEquals(OffsetDateTime.of(2026, 6, 23, 6, 0, 0, 0, ZoneOffset.UTC), query.startedAt)
        assertEquals("SELECT * FROM auction", query.info)
        assertEquals(1, query.stage)
        assertEquals(2, query.maxStage)
        assertEquals(50.5, query.progress)
        assertEquals(10, query.memoryUsed)
        assertEquals(20, query.examinedRows)
    }

    @Test
    fun `maps table size rows`() {
        val resultSet = mockk<ResultSet>()
        every { resultSet.getString("name") } returns "auction"
        every { resultSet.getLong("tableRows") } returns 100
        every { resultSet.getLong("tableSizeInMb") } returns 10
        every { resultSet.getLong("indexSizeInMb") } returns 20
        every { resultSet.getLong("sizeInMb") } returns 30
        every { resultSet.getLong("freeTableSizeInMb") } returns 4
        every { resultSet.getLong("allocatedTableSize") } returns 34
        every {
            jdbcTemplate.query(match<String> { it.contains("information_schema.TABLES") }, any<RowMapper<AdminTableSize>>())
        } answers {
            listOf(secondArg<RowMapper<AdminTableSize>>().mapRow(resultSet, 0))
        }

        val table = service.getTableSizes().single()

        assertEquals("auction", table.name)
        assertEquals(100, table.rows)
        assertEquals(10, table.tableSizeInMb)
        assertEquals(20, table.indexSizeInMb)
        assertEquals(30, table.sizeInMb)
        assertEquals(4, table.freeTableSizeInMb)
        assertEquals(34, table.allocatedTableSize)
    }
}
