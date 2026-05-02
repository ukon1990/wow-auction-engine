package net.jonasmf.auctionengine.utility

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun datesBetween(
    startDate: LocalDate,
    endDate: LocalDate,
): List<LocalDate> {
    require(!endDate.isBefore(startDate)) { "End date must not be before start date" }
    val dates = mutableListOf<LocalDate>()
    val days = ChronoUnit.DAYS.between(startDate, endDate)
    return (0..days).map { startDate.plusDays(it) }
}
