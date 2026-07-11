package net.jonasmf.auctionengine.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BranchDatabaseConfigTest {
    @Test
    fun `master branch uses default database`() {
        assertEquals(
            "dbo",
            resolveLocalDevDatabaseName("master", "dbo", "dbo"),
        )
    }

    @Test
    fun `branch names are converted to deterministic MariaDB database names`() {
        assertEquals(
            "branch_feat_73_branch_database_copy_7bfd27a3",
            "feat/73-branch-database-copy".toMariaDbDatabaseName(),
        )
    }

    @Test
    fun `long branch database names are kept within MariaDB identifier limit`() {
        val databaseName =
            "feat/73-copy-over-the-code-from-flavor-forge-and-adapt-it-to-this-project".toMariaDbDatabaseName()

        assertEquals(64, databaseName.length)
        assertEquals("branch_feat_73_copy_over_the_code_from_flavor_forge_and_df781e52", databaseName)
    }

    @Test
    fun `database can be removed from JDBC URL while preserving query parameters`() {
        assertEquals(
            "jdbc:mariadb://localhost:59000/?serverTimezone=UTC&cachePrepStmts=true",
            "jdbc:mariadb://localhost:59000/dbo?serverTimezone=UTC&cachePrepStmts=true".withoutDatabase(),
        )
    }

    @Test
    fun `selected database can be applied to JDBC URL while preserving query parameters`() {
        assertEquals(
            "jdbc:mariadb://localhost:59000/branch_feature?serverTimezone=UTC&cachePrepStmts=true",
            "jdbc:mariadb://localhost:59000/dbo?serverTimezone=UTC&cachePrepStmts=true".withDatabase("branch_feature"),
        )
    }

    @Test
    fun `branch database clone qualifies copied sequence DDL with target database`() {
        assertEquals(
            "CREATE SEQUENCE `branch_feature`.`auction_house_seq` start with 1 increment by 50",
            "CREATE SEQUENCE `auction_house_seq` start with 1 increment by 50"
                .qualifyCreateTableOrSequence("branch_feature", "auction_house_seq"),
        )
    }

    @Test
    fun `branch database clone requalifies schema-qualified sequence DDL with target database`() {
        assertEquals(
            "CREATE SEQUENCE `branch_feature`.`auction_house_seq` start with 1 increment by 50",
            "CREATE SEQUENCE `dbo`.`auction_house_seq` start with 1 increment by 50"
                .qualifyCreateTableOrSequence("branch_feature", "auction_house_seq"),
        )
    }

    @Test
    fun `branch database clone qualifies copied table DDL with target database`() {
        assertEquals(
            "CREATE TABLE `branch_feature`.`auction_house` (`id` int not null)",
            "CREATE TABLE `auction_house` (`id` int not null)"
                .qualifyCreateTableOrSequence("branch_feature", "auction_house"),
        )
    }

    @Test
    fun `branch database clone skips volatile runtime table data`() {
        assertNull(
            dataCopySql(
                sourceDatabase = "dbo",
                targetDatabase = "branch_feature",
                tableName = "auction_price",
            ),
        )
    }

    @Test
    fun `branch database clone bounds historical stats data`() {
        assertEquals(
            """
            INSERT INTO `branch_feature`.`auction_stats_hourly`
            SELECT *
            FROM `dbo`.`auction_stats_hourly`
            WHERE `date` >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            """.trimIndent(),
            dataCopySql(
                sourceDatabase = "dbo",
                targetDatabase = "branch_feature",
                tableName = "auction_stats_hourly",
            ),
        )
    }

    @Test
    fun `branch database clone keeps reference data`() {
        assertEquals(
            """
            INSERT INTO `branch_feature`.`item`
            SELECT *
            FROM `dbo`.`item`
            """.trimIndent(),
            dataCopySql(
                sourceDatabase = "dbo",
                targetDatabase = "branch_feature",
                tableName = "item",
            ),
        )
    }

    @Test
    fun `branch database clone orders dependent views after their dependencies`() {
        assertEquals(
            listOf("v_item", "v_auction_market_item_details"),
            orderedViewNames(
                mapOf(
                    "v_auction_market_item_details" to "SELECT * FROM v_item",
                    "v_item" to "SELECT * FROM item",
                ),
            ),
        )
    }

    @Test
    fun `branch database clone does not treat partial view name matches as dependencies`() {
        assertEquals(
            listOf("v_item", "v_item_detail"),
            orderedViewNames(
                mapOf(
                    "v_item" to "SELECT * FROM item",
                    "v_item_detail" to "SELECT * FROM item_detail",
                ),
            ),
        )
    }

    @Test
    fun `branch database clone makes plain view creation idempotent`() {
        assertEquals(
            "CREATE OR REPLACE VIEW `branch_feature`.`v_auction_house_daily_prices` AS SELECT * FROM `branch_feature`.`auction_stats_daily`",
            cloneViewSql(
                createViewSql =
                    "CREATE VIEW `v_auction_house_daily_prices` AS SELECT * FROM `dbo`.`auction_stats_daily`",
                sourceDatabase = "dbo",
                targetDatabase = "branch_feature",
                viewName = "v_auction_house_daily_prices",
            ),
        )
    }

    @Test
    fun `branch database clone normalizes MariaDB show create view output`() {
        assertEquals(
            "CREATE OR REPLACE VIEW `branch_feature`.`v_auction_house_daily_prices` AS SELECT 1 AS `value`",
            cloneViewSql(
                createViewSql =
                    "CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`%` SQL SECURITY DEFINER VIEW `v_auction_house_daily_prices` AS SELECT 1 AS `value`",
                sourceDatabase = "dbo",
                targetDatabase = "branch_feature",
                viewName = "v_auction_house_daily_prices",
            ),
        )
    }

    @Test
    fun `branch database clone preserves idempotent qualified view creation`() {
        assertEquals(
            "CREATE OR REPLACE VIEW `branch_feature`.`v_auction_house_daily_prices` AS SELECT 1 AS `value`",
            cloneViewSql(
                createViewSql =
                    "CREATE OR REPLACE VIEW `dbo`.`v_auction_house_daily_prices` AS SELECT 1 AS `value`",
                sourceDatabase = "dbo",
                targetDatabase = "branch_feature",
                viewName = "v_auction_house_daily_prices",
            ),
        )
    }

    @Test
    fun `existing branch database only clones missing views`() {
        assertEquals(
            setOf("v_recipe"),
            missingViewNames(
                sourceViewNames = setOf("v_auction_house_daily_prices", "v_recipe"),
                targetViewNames = setOf("v_auction_house_daily_prices"),
            ),
        )
    }
}
