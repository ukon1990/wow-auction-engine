package net.jonasmf.auctionengine.testsupport.database

import io.awspring.cloud.dynamodb.DynamoDbOperations
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class TestDataCleaner(
    private val jdbcTemplate: JdbcTemplate,
    private val dynamoDbOperations: DynamoDbOperations,
) {
    fun resetRelationalDatabase() {
        val tableNames =
            jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                """.trimIndent(),
                String::class.java,
            )

        if (tableNames.isEmpty()) {
            return
        }

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0")
        try {
            tableNames.forEach { tableName ->
                jdbcTemplate.execute("TRUNCATE TABLE `$tableName`")
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1")
        }
    }

    fun <T : Any> clearDynamoTable(clazz: Class<T>) {
        val allEntities = dynamoDbOperations.scanAll(clazz)
        allEntities.items().forEach { entity ->
            dynamoDbOperations.delete<T>(entity)
        }
    }
}
