package net.jonasmf.auctionengine.config

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
class DataSourceConfig {
    @Bean
    fun dataSource(
        dataSourceProperties: DataSourceProperties,
        selectedDatabase: SelectedDatabase,
        branchDatabaseCloner: BranchDatabaseCloner,
    ): DataSource {
        val jdbcUrl = dataSourceProperties.url.orEmpty()
        if (!selectedDatabase.branchDatabaseEnabled) {
            return dataSourceProperties.initializeDataSourceBuilder().build()
        }

        branchDatabaseCloner.prepareBranchDatabase(
            jdbcUrl = jdbcUrl,
            username = dataSourceProperties.username,
            password = dataSourceProperties.password,
            selectedDatabase = selectedDatabase,
        )

        dataSourceProperties.url = jdbcUrl.withDatabase(selectedDatabase.name)
        return dataSourceProperties.initializeDataSourceBuilder().build()
    }
}

internal fun String.withDatabase(database: String): String {
    val prefix = "jdbc:mariadb://"
    if (!startsWith(prefix)) return this
    val queryIndex = indexOf('?')
    val query = if (queryIndex == -1) "" else substring(queryIndex)
    val withoutQuery = if (queryIndex == -1) this else substring(0, queryIndex)
    val pathIndex = withoutQuery.indexOf('/', prefix.length)
    return if (pathIndex == -1) {
        "$withoutQuery/$database$query"
    } else {
        withoutQuery.substring(0, pathIndex + 1) + database + query
    }
}
