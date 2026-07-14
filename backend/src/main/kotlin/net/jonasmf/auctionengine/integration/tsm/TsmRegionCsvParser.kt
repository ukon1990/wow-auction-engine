package net.jonasmf.auctionengine.integration.tsm

import java.math.BigDecimal
import java.time.Instant

data class TsmRegionCsvRow(
    val subjectId: Int,
    val saleRate: BigDecimal,
    val soldPerDay: BigDecimal,
    val marketValue: Long?,
    val historical: Long?,
    val avgSalePrice: Long?,
    val sourceUpdatedAt: Instant,
)

private const val COL_MARKET_VALUE = "marketValue"
private const val COL_HISTORICAL = "historical"
private const val COL_AVG_SALE_PRICE = "avgSalePrice"
private const val COL_SALE_RATE = "saleRate"
private const val COL_SOLD_PER_DAY = "soldPerDay"
private const val COL_UPDATED_AT = "updatedAt"

fun parseTsmRegionCsv(
    csv: String,
    idColumn: String,
): List<TsmRegionCsvRow> {
    val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
    require(lines.isNotEmpty()) { "TSM CSV is empty" }

    val headers = parseCsvLine(lines.first()).map { it.trim() }
    val indexByHeader = headers.withIndex().associate { (index, header) -> header to index }

    fun requireColumn(name: String): Int =
        indexByHeader[name]
            ?: throw IllegalArgumentException("TSM CSV missing required column: $name")

    val idIndex = requireColumn(idColumn)
    val marketValueIndex = requireColumn(COL_MARKET_VALUE)
    val historicalIndex = requireColumn(COL_HISTORICAL)
    val avgSalePriceIndex = requireColumn(COL_AVG_SALE_PRICE)
    val saleRateIndex = requireColumn(COL_SALE_RATE)
    val soldPerDayIndex = requireColumn(COL_SOLD_PER_DAY)
    val updatedAtIndex = requireColumn(COL_UPDATED_AT)

    return lines
        .drop(1)
        .map { line ->
            val columns = parseCsvLine(line)
            require(columns.size == headers.size) {
                "TSM CSV row has ${columns.size} columns, expected ${headers.size}: $line"
            }
            TsmRegionCsvRow(
                subjectId = columns[idIndex].trim().toInt(),
                saleRate = columns[saleRateIndex].trim().toBigDecimal(),
                soldPerDay = columns[soldPerDayIndex].trim().toBigDecimal(),
                marketValue = columns[marketValueIndex].trim().toNullableLong(),
                historical = columns[historicalIndex].trim().toNullableLong(),
                avgSalePrice = columns[avgSalePriceIndex].trim().toNullableLong(),
                sourceUpdatedAt = parseTsmUpdatedAt(columns[updatedAtIndex].trim()),
            )
        }
}

internal fun parseCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' -> {
                if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index++
                } else {
                    inQuotes = !inQuotes
                }
            }
            char == ',' && !inQuotes -> {
                fields.add(current.toString())
                current.clear()
            }
            else -> current.append(char)
        }
        index++
    }
    fields.add(current.toString())
    return fields
}

internal fun parseTsmUpdatedAt(raw: String): Instant {
    require(raw.isNotBlank()) { "TSM updatedAt must not be blank" }
    if (raw.all { it.isDigit() }) {
        val epoch = raw.toLong()
        return if (epoch >= 1_000_000_000_000L) {
            Instant.ofEpochMilli(epoch)
        } else {
            Instant.ofEpochSecond(epoch)
        }
    }
    return Instant.parse(raw)
}

private fun String.toNullableLong(): Long? = if (isEmpty()) null else toLong()
