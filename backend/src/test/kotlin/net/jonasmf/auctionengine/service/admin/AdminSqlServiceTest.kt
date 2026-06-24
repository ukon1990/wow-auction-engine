package net.jonasmf.auctionengine.service.admin

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.jonasmf.auctionengine.generated.model.AdminSqlExecuteRequest
import net.jonasmf.auctionengine.generated.model.AdminSqlResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCreator
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.RowMapper
import org.springframework.web.server.ResponseStatusException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException

class AdminSqlServiceTest {
    private val jdbcTemplate = mockk<JdbcTemplate>()
    private val service = AdminSqlService(jdbcTemplate)

    @Test
    fun `appends default limit to select queries`() {
        val result = executeSuccessfully("SELECT id FROM auction")

        assertEquals("SELECT id FROM auction LIMIT 500", result.effectiveSql)
        assertEquals(AdminSqlResult.Mode.QUERY, result.mode)
        assertEquals(listOf("id"), result.columns)
        assertEquals(listOf(listOf("123")), result.rows)
    }

    @Test
    fun `accepts mixed-case select queries`() {
        val result =
            executeSuccessfully(
                """
                SElECT *
                FROM items
                """.trimIndent(),
            )

        assertEquals(
            """
            SElECT *
            FROM items LIMIT 500
            """.trimIndent(),
            result.effectiveSql,
        )
    }

    @Test
    fun `preserves safe top-level limit`() {
        val result = executeSuccessfully("SELECT id FROM auction LIMIT 25")

        assertEquals("SELECT id FROM auction LIMIT 25", result.effectiveSql)
    }

    @Test
    fun `rejects top-level limit above cap while limiting enabled`() {
        assertBadRequest("SELECT id FROM auction LIMIT 501")
    }

    @Test
    fun `does not append limit when limiting is disabled`() {
        val result = executeSuccessfully("SELECT id FROM auction", limitRows = false)

        assertEquals("SELECT id FROM auction", result.effectiveSql)
    }

    @Test
    fun `does not rewrite show or describe diagnostics`() {
        assertEquals("SHOW TABLES", executeSuccessfully("SHOW TABLES").effectiveSql)
        assertEquals("DESCRIBE auction", executeSuccessfully("DESCRIBE auction").effectiveSql)
    }

    @Test
    fun `uses effective sql for explain and analyze`() {
        val explain =
            executeSuccessfully(
                "WITH rows AS (SELECT id FROM auction) SELECT id FROM rows",
                mode = AdminSqlExecuteRequest.Mode.EXPLAIN,
            )
        val analyze =
            executeSuccessfully(
                "SELECT id FROM auction",
                mode = AdminSqlExecuteRequest.Mode.ANALYZE,
            )

        assertEquals(
            "EXPLAIN FORMAT=JSON WITH rows AS (SELECT id FROM auction) SELECT id FROM rows LIMIT 500",
            explain.effectiveSql,
        )
        assertEquals("ANALYZE FORMAT=JSON SELECT id FROM auction LIMIT 500", analyze.effectiveSql)
    }

    @Test
    fun `rejects explain and analyze for non select diagnostics`() {
        assertBadRequest("SHOW TABLES", mode = AdminSqlExecuteRequest.Mode.EXPLAIN)
        assertBadRequest("DESCRIBE auction", mode = AdminSqlExecuteRequest.Mode.ANALYZE)
    }

    @Test
    fun `rejects destructive and administrative sql`() {
        listOf(
            "UPDATE auction SET item_id = 1",
            "DELETE FROM auction",
            "INSERT INTO auction (id) VALUES (1)",
            "DROP TABLE auction",
            "ALTER TABLE auction ADD COLUMN x INT",
            "TRUNCATE TABLE auction",
            "ANALYZE TABLE auction",
        ).forEach { sql -> assertBadRequest(sql) }
    }

    @Test
    fun `rejects multiple statements`() {
        assertBadRequest("SELECT 1; SELECT 2")
    }

    @Test
    fun `ignores blocked keywords in strings and comments`() {
        val result =
            executeSuccessfully(
                """
                SELECT 'delete from auction' AS text
                -- update auction
                FROM auction
                """.trimIndent(),
            )

        assertTrue(result.effectiveSql.endsWith("LIMIT 500"))
    }

    @Test
    fun `marks result as truncated when jdbc returns one extra row`() {
        val result = executeSuccessfully("SELECT id FROM auction", rowValues = listOf("1", "2"), rowLimit = 1)

        assertEquals(1, result.rowCount)
        assertTrue(result.truncated)
        assertEquals(listOf(listOf("1")), result.rows)
    }

    @Test
    fun `returns safe jdbc execution error as bad request reason`() {
        every {
            jdbcTemplate.query(any<PreparedStatementCreator>(), any<ResultSetExtractor<Any>>())
        } throws
            BadSqlGrammarException(
                "admin sql",
                "SELECT * FROM items LIMIT 500",
                SQLException("Table 'dbo.items' doesn't exist"),
            )

        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.execute(
                    AdminSqlExecuteRequest(
                        sql = "SELECT * FROM items",
                        mode = AdminSqlExecuteRequest.Mode.QUERY,
                        limitRows = true,
                    ),
                )
            }

        assertEquals(400, error.statusCode.value())
        assertEquals("Table 'dbo.items' doesn't exist", error.reason)
    }

    @Test
    fun `maps sql metadata tables columns and indexes`() {
        val tableSet = mockk<ResultSet>()
        every { tableSet.getString("name") } returns "auction"
        every { tableSet.getString("engine") } returns "InnoDB"
        every { tableSet.getLong("tableRows") } returns 12
        every { tableSet.wasNull() } returns false
        every {
            jdbcTemplate.query(match<String> { it.contains("information_schema.TABLES") }, any<RowMapper<Any>>())
        } answers {
            listOf(secondArg<RowMapper<Any>>().mapRow(tableSet, 0))
        }

        val columnSet = mockk<ResultSet>()
        every { columnSet.getString("tableName") } returns "auction"
        every { columnSet.getString("name") } returns "id"
        every { columnSet.getString("dataType") } returns "bigint"
        every { columnSet.getString("columnType") } returns "bigint(20)"
        every { columnSet.getString("nullable") } returns "NO"
        every { columnSet.getString("defaultValue") } returns null
        every { columnSet.getString("extra") } returns "auto_increment"
        every { columnSet.getInt("ordinalPosition") } returns 1
        every {
            jdbcTemplate.query(match<String> { it.contains("information_schema.COLUMNS") }, any<RowMapper<Any>>())
        } answers {
            listOf(secondArg<RowMapper<Any>>().mapRow(columnSet, 0))
        }

        val indexSet = mockk<ResultSet>()
        every { indexSet.getString("tableName") } returns "auction"
        every { indexSet.getString("name") } returns "PRIMARY"
        every { indexSet.getInt("nonUnique") } returns 0
        every { indexSet.getString("columnName") } returns "id"
        every { indexSet.getInt("sequenceInIndex") } returns 1
        every {
            jdbcTemplate.query(match<String> { it.contains("information_schema.STATISTICS") }, any<RowMapper<Any>>())
        } answers {
            listOf(secondArg<RowMapper<Any>>().mapRow(indexSet, 0))
        }

        val table = service.getMetadata().tables.single()

        assertEquals("auction", table.name)
        assertEquals("InnoDB", table.engine)
        assertEquals(12, table.tableRows)
        assertEquals("id", table.columns.single().name)
        assertFalse(table.columns.single().nullable)
        assertEquals("PRIMARY", table.indexes.single().name)
        assertTrue(table.indexes.single().unique)
        assertEquals(listOf("id"), table.indexes.single().columns)
    }

    private fun executeSuccessfully(
        sql: String,
        mode: AdminSqlExecuteRequest.Mode = AdminSqlExecuteRequest.Mode.QUERY,
        limitRows: Boolean = true,
        rowLimit: Int? = null,
        rowValues: List<String> = listOf("123"),
    ): AdminSqlResult {
        mockJdbcResult(rowValues)
        return service.execute(
            AdminSqlExecuteRequest(
                sql = sql,
                mode = mode,
                limitRows = limitRows,
                rowLimit = rowLimit,
            ),
        )
    }

    private fun assertBadRequest(
        sql: String,
        mode: AdminSqlExecuteRequest.Mode = AdminSqlExecuteRequest.Mode.QUERY,
    ) {
        val error =
            assertThrows(ResponseStatusException::class.java) {
                service.execute(
                    AdminSqlExecuteRequest(
                        sql = sql,
                        mode = mode,
                        limitRows = true,
                    ),
                )
            }

        assertEquals(400, error.statusCode.value())
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockJdbcResult(rowValues: List<String>) {
        val creatorSlot = slot<PreparedStatementCreator>()
        val extractorSlot = slot<ResultSetExtractor<Any>>()
        every {
            jdbcTemplate.query(capture(creatorSlot), capture(extractorSlot))
        } answers {
            val connection = mockk<Connection>()
            val statement = mockk<PreparedStatement>(relaxed = true)
            every { connection.prepareStatement(any()) } returns statement
            creatorSlot.captured.createPreparedStatement(connection)

            extractorSlot.captured.extractData(resultSet(rowValues)) as Any
        }
    }

    private fun resultSet(rowValues: List<String>): ResultSet {
        val metadata = mockk<ResultSetMetaData>()
        every { metadata.columnCount } returns 1
        every { metadata.getColumnLabel(1) } returns "id"
        every { metadata.getColumnName(1) } returns "id"

        val resultSet = mockk<ResultSet>()
        var index = -1
        every { resultSet.metaData } returns metadata
        every { resultSet.next() } answers {
            index += 1
            index < rowValues.size
        }
        every { resultSet.getObject(1) } answers { rowValues[index] }
        return resultSet
    }
}
