package net.jonasmf.auctionengine.config

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties

class DataSourceConfigTest {
    @Test
    fun `data source URL is not rewritten when branch database mode is disabled`() {
        val dataSourceProperties =
            DataSourceProperties().apply {
                url = "jdbc:mariadb://localhost:12345/test"
                username = "root"
                password = "root"
                driverClassName = "org.mariadb.jdbc.Driver"
            }
        val branchDatabaseCloner = mockk<BranchDatabaseCloner>(relaxed = true)
        val selectedDatabase =
            SelectedDatabase(
                name = "dbo",
                defaultDatabase = "dbo",
                cloneSourceDatabase = "dbo",
                branchDatabaseEnabled = false,
            )

        DataSourceConfig().dataSource(
            dataSourceProperties = dataSourceProperties,
            selectedDatabase = selectedDatabase,
            branchDatabaseCloner = branchDatabaseCloner,
        )

        assertEquals("jdbc:mariadb://localhost:12345/test", dataSourceProperties.url)
        verify(exactly = 0) {
            branchDatabaseCloner.prepareBranchDatabase(any(), any(), any(), any())
        }
    }
}
