package net.jonasmf.auctionengine.constant.admin

import net.jonasmf.auctionengine.generated.model.AuctionHousePageSortBy

enum class AdminAuctionHousePageSortBy(
    val value: String,
) {
    NAME("realm_name"),
    REGION("auction_house_region"),
    LOWEST_DELAY("auction_house_lowest_delay"),
    AVG_DELAY("auction_house_avg_delay"),
    HIGHEST_DELAY("auction_house_highest_delay"),
    LAST_MODIFIED("auction_house_last_modified"),
    LAST_AUCTION_PRICE_DELETE_EVENT("auction_house_last_auction_price_delete_event"),
    LAST_HISTORY_DELETE_EVENT("auction_house_last_history_delete_event"),
    LAST_HISTORY_DELETE_EVENT_DAILY("auction_house_last_history_delete_event_daily"),
    NEXT_UPDATE("auction_house_next_update"),
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
