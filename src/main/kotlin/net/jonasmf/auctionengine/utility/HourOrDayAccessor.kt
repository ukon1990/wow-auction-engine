package net.jonasmf.auctionengine.utility

import net.jonasmf.auctionengine.dbo.rds.auction.DailyAuctionStats
import net.jonasmf.auctionengine.dbo.rds.auction.HourlyAuctionStats
import java.lang.reflect.Field

private object HourAccessors {
    private fun field(name: String): Field =
        HourlyAuctionStats::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }

    val priceByHour: Map<Int, Field> =
        (0..23).associateWith { hour -> field("price%02d".format(hour)) }

    val quantityByHour: Map<Int, Field> =
        (0..23).associateWith { hour -> field("quantity%02d".format(hour)) }
}

private object DayOfMonthAccessors {
    private fun field(name: String): Field =
        DailyAuctionStats::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }

    // The old API exposed a single price/quantity setter per day. The current daily
    // model stores min/avg/max, so this maps to the avg slots until richer daily
    // aggregation is implemented.
    val priceByDayOfMonth: Map<Int, Field> =
        (1..31).associateWith { dayOfMonth -> field("avg%02d".format(dayOfMonth)) }

    val quantityByDayOfMonth: Map<Int, Field> =
        (1..31).associateWith { dayOfMonth -> field("avgQuantity%02d".format(dayOfMonth)) }
}

fun HourlyAuctionStats.setHourValues(
    hour: Int,
    price: Long?,
    quantity: Long?,
) {
    require(hour in 0..23) { "hour must be 0..23, was $hour" }
    HourAccessors.priceByHour.getValue(hour).set(this, price)
    HourAccessors.quantityByHour.getValue(hour).set(this, quantity)
}

fun DailyAuctionStats.setDayOfMonthValues(
    dayOfMonth: Int,
    price: Long?,
    quantity: Long?,
) {
    require(dayOfMonth in 1..31) { "dayOfMonth must be 1..31, was $dayOfMonth" }
    DayOfMonthAccessors.priceByDayOfMonth.getValue(dayOfMonth).set(this, price)
    DayOfMonthAccessors.quantityByDayOfMonth.getValue(dayOfMonth).set(this, quantity)
}
