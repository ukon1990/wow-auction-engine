package net.jonasmf.auctionengine.utility

import net.jonasmf.auctionengine.dbo.rds.auction.DailyAuctionStats
import net.jonasmf.auctionengine.dbo.rds.auction.HourlyAuctionStats
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

private object HourAccessors {
    private fun prop(name: String): KMutableProperty1<HourlyAuctionStats, Long?> =
        HourlyAuctionStats::class
            .memberProperties
            .first { it.name == name } as KMutableProperty1<HourlyAuctionStats, Long?>

    // Cache alle 24 par av (price, quantity)
    val priceByHour: Map<Int, KMutableProperty1<HourlyAuctionStats, Long?>> =
        (0..23).associateWith { h -> prop("price%02d".format(h)) }

    val quantityByHour: Map<Int, KMutableProperty1<HourlyAuctionStats, Long?>> =
        (0..23).associateWith { h -> prop("quantity%02d".format(h)) }
}

private object DayOfMonthAccessors {
    private fun prop(name: String): KMutableProperty1<DailyAuctionStats, Long?> =
        HourlyAuctionStats::class
            .memberProperties
            .first { it.name == name } as KMutableProperty1<DailyAuctionStats, Long?>

    // Cache alle 31 par av (price, quantity)
    val priceByDayOfMonth: Map<Int, KMutableProperty1<DailyAuctionStats, Long?>> =
        (1..31).associateWith { d -> prop("priceDay%02d".format(d)) }
    val quantityByDayOfMonth: Map<Int, KMutableProperty1<DailyAuctionStats, Long?>> =
        (1..31).associateWith { d -> prop("quantityDay%02d".format(d)) }
}

/** Oppdaterer priceHH/quantityHH basert på [hour] (0–23). */
fun HourlyAuctionStats.setHourValues(
    hour: Int,
    price: Long?,
    quantity: Long?,
) {
    require(hour in 0..23) { "hour must be 0..23, was $hour" }
    HourAccessors.priceByHour.getValue(hour).set(this, price)
    HourAccessors.quantityByHour.getValue(hour).set(this, quantity)
}

/** Henter priceHH/quantityHH basert på [hour] (0–23). */
fun DailyAuctionStats.setDayOfMonthValues(
    dayOfMonth: Int,
    price: Long?,
    quantity: Long?,
) {
    require(dayOfMonth in 1..31) { "dayOfMonth must be 1..31, was $dayOfMonth" }
    DayOfMonthAccessors.priceByDayOfMonth.getValue(dayOfMonth).set(this, price)
    DayOfMonthAccessors.quantityByDayOfMonth.getValue(dayOfMonth).set(this, quantity)
}
