package net.jonasmf.auctionengine.constant.admin

import net.jonasmf.auctionengine.generated.model.AuctionHousePageSortBy

enum class AdminAuctionHousePageSortBy(
    val value: String,
) {
    NAME("name"),
    REGION("ah.region"),
    LOWEST_DELAY("ah.lowest_delay"),
    AVG_DELAY("ah.avg_delay"),
    HIGHEST_DELAY("ah.highest_delay"),
    LAST_MODIFIED("ah.last_modified"),
    LAST_AUCTION_PRICE_DELETE_EVENT("ah.last_auction_price_delete_event"),
    LAST_HISTORY_DELETE_EVENT("ah.last_history_delete_event"),
    LAST_HISTORY_DELETE_EVENT_DAILY("ah.last_history_delete_event_daily"),
    NEXT_UPDATE("ah.next_update"),
}

fun AuctionHousePageSortBy.toDomain(): AdminAuctionHousePageSortBy =
    when (this) {
        AuctionHousePageSortBy.NAME -> {
            AdminAuctionHousePageSortBy.NAME
        }

        AuctionHousePageSortBy.REGION -> {
            AdminAuctionHousePageSortBy.REGION
        }

        AuctionHousePageSortBy.LOWEST_DELAY -> {
            AdminAuctionHousePageSortBy.LOWEST_DELAY
        }

        AuctionHousePageSortBy.AVG_DELAY -> {
            AdminAuctionHousePageSortBy.AVG_DELAY
        }

        AuctionHousePageSortBy.HIGHEST_DELAY -> {
            AdminAuctionHousePageSortBy.HIGHEST_DELAY
        }

        AuctionHousePageSortBy.LAST_MODIFIED -> {
            AdminAuctionHousePageSortBy.LAST_MODIFIED
        }

        AuctionHousePageSortBy.LAST_AUCTION_PRICE_DELETE_EVENT -> {
            AdminAuctionHousePageSortBy.LAST_AUCTION_PRICE_DELETE_EVENT
        }

        AuctionHousePageSortBy.LAST_HISTORY_DELETE_EVENT -> {
            AdminAuctionHousePageSortBy.LAST_HISTORY_DELETE_EVENT
        }

        AuctionHousePageSortBy.LAST_HISTORY_DELETE_EVENT_DAILY -> {
            AdminAuctionHousePageSortBy.LAST_HISTORY_DELETE_EVENT_DAILY
        }

        AuctionHousePageSortBy.NEXT_UPDATE -> {
            AdminAuctionHousePageSortBy.NEXT_UPDATE
        }

        else -> {
            throw IllegalArgumentException("Unknown $this")
        }
    }
