package net.jonasmf.auctionengine.config

import org.junit.jupiter.api.Assertions.assertEquals
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
}
