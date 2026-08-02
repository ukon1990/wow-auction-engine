package net.jonasmf.auctionengine.repository.rds

internal fun hourColumnSuffix(hourOfDay: Int): String = hourOfDay.coerceIn(0, 23).toString().padStart(2, '0')

internal fun hoursCteSql(): String = (0..23).joinToString(separator = " UNION ALL ") { "SELECT $it AS hour_of_day" }

/** Builds the SQL expression that selects a price column for an hour. */
internal fun hourlyPriceCaseExpression(
    tableAlias: String = "ash",
    hourCol: String = "h.hour_of_day",
): String = hourlyCaseExpression(tableAlias, hourCol, "price")

/** Builds the SQL expression that selects a quantity column for an hour. */
internal fun hourlyQuantityCaseExpression(
    tableAlias: String = "ash",
    hourCol: String = "h.hour_of_day",
): String = hourlyCaseExpression(tableAlias, hourCol, "quantity")

private fun hourlyCaseExpression(
    tableAlias: String,
    hourColumn: String,
    valueColumnPrefix: String,
): String =
    (0..23).joinToString(prefix = "CASE $hourColumn ", separator = " ", postfix = " END") {
        "WHEN $it THEN $tableAlias.$valueColumnPrefix${hourColumnSuffix(it)}"
    }
