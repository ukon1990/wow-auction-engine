package net.jonasmf.auctionengine.repository.rds

/** Shared helpers for chunked multi-row JDBC UPSERT / INSERT statements. */
internal object JdbcBatchSupport {
    const val MAX_PREPARED_STATEMENT_PLACEHOLDERS = 60_000

    fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")

    fun rowPlaceholders(
        rowCount: Int,
        columnCount: Int,
    ): String = List(rowCount) { "(${placeholders(columnCount)})" }.joinToString(",")

    fun maxRowsPerStatement(columnCount: Int): Int = MAX_PREPARED_STATEMENT_PLACEHOLDERS / columnCount
}
