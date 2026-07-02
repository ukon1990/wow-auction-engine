package net.jonasmf.auctionengine.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.io.File
import java.net.URI
import java.security.MessageDigest

private const val MASTER_BRANCH = "master"
private const val BRANCH_DATABASE_PREFIX = "branch_"
private const val MAX_DATABASE_NAME_LENGTH = 64
private const val DATABASE_HASH_LENGTH = 8
private val MARIA_DB_IDENTIFIER_PATTERN = Regex("[A-Za-z0-9_$]+")

data class SelectedDatabase(
    val name: String,
    val defaultDatabase: String,
    val cloneSourceDatabase: String,
    val branchDatabaseEnabled: Boolean,
) {
    val shouldCloneFromTemplate: Boolean =
        branchDatabaseEnabled && name != defaultDatabase && name != cloneSourceDatabase
}

@Configuration(proxyBeanMethods = false)
class BranchDatabaseConfig {
    @Bean
    fun selectedDatabase(
        environment: Environment,
        dataSourceProperties: DataSourceProperties,
        @Value("\${wae.dev.branch-database.enabled:true}") enabled: Boolean,
        @Value("\${wae.dev.branch-database.default-database:dbo}") defaultDatabaseProperty: String,
        @Value("\${wae.dev.branch-database.template-database:dbo}") templateDatabaseProperty: String,
    ): SelectedDatabase {
        val defaultDatabase = normalizeConfiguredDatabase(defaultDatabaseProperty, "default-database")
        val templateDatabase = normalizeConfiguredDatabase(templateDatabaseProperty, "template-database")
        if (!enabled || !isLocalBranchDatabaseContext(environment, dataSourceProperties.url.orEmpty())) {
            return SelectedDatabase(
                name = defaultDatabase,
                defaultDatabase = defaultDatabase,
                cloneSourceDatabase = templateDatabase,
                branchDatabaseEnabled = false,
            )
        }

        val branchName = currentGitBranch()
        val databaseName = resolveLocalDevDatabaseName(branchName, defaultDatabase, templateDatabase)

        return SelectedDatabase(
            name = databaseName,
            defaultDatabase = defaultDatabase,
            cloneSourceDatabase = templateDatabase,
            branchDatabaseEnabled = true,
        )
    }
}

private fun normalizeConfiguredDatabase(
    database: String,
    propertyName: String,
): String {
    val normalized = database.trim()
    require(MARIA_DB_IDENTIFIER_PATTERN.matches(normalized)) {
        "wae.dev.branch-database.$propertyName must be an unquoted MariaDB identifier"
    }
    return normalized
}

private fun isLocalBranchDatabaseContext(
    environment: Environment,
    configuredUrl: String,
): Boolean {
    if (System.getenv("CI") == "true") return false
    if (environment.activeProfiles.any { it == "production" || it == "test" }) return false
    return configuredUrl.isDocumentedLocalMariaDbUrl()
}

private fun String.isDocumentedLocalMariaDbUrl(): Boolean =
    runCatching {
        val uri = URI(replaceFirst("jdbc:mariadb://", "http://"))
        val port = if (uri.port == -1) 3306 else uri.port
        uri.host in setOf("localhost", "127.0.0.1", "::1") && port == 59000
    }.getOrDefault(false)

private fun currentGitBranch(): String? {
    val logger = LoggerFactory.getLogger("net.jonasmf.auctionengine.config.BranchDatabaseConfig")
    return runCatching {
        val process =
            ProcessBuilder("git", "branch", "--show-current")
                .directory(File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .start()
        val output =
            process.inputStream
                .bufferedReader()
                .use { it.readText() }
                .trim()
        if (process.waitFor() != 0 || output.isBlank()) null else output
    }.onFailure {
        logger.debug("Unable to detect git branch for dev database selection", it)
    }.getOrNull()
}

internal fun resolveLocalDevDatabaseName(
    branchName: String?,
    defaultDatabase: String,
    templateDatabase: String,
): String =
    when {
        branchName == null || branchName == MASTER_BRANCH -> defaultDatabase
        branchName.equals(templateDatabase, ignoreCase = true) -> templateDatabase
        else -> branchName.toMariaDbDatabaseName()
    }

internal fun String.toMariaDbDatabaseName(): String {
    val normalized =
        lowercase()
            .replace(Regex("[^a-z0-9_$]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "branch" }
    val hash = sha1Hex().take(DATABASE_HASH_LENGTH)
    val suffix = "_$hash"
    val maxNormalizedLength = MAX_DATABASE_NAME_LENGTH - BRANCH_DATABASE_PREFIX.length - suffix.length
    val trimmed = normalized.take(maxNormalizedLength).trimEnd('_').ifBlank { "branch" }
    return "$BRANCH_DATABASE_PREFIX$trimmed$suffix"
}

private fun String.sha1Hex(): String =
    MessageDigest
        .getInstance("SHA-1")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
