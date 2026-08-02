package net.jonasmf.auctionengine.repository.rds

internal val AUCTION_MARKET_SORT_COLUMNS =
    mapOf(
        "itemName" to "item_name",
        "quality" to "quality_name",
        "itemClass" to "item_class_name",
        "itemSubclass" to "item_subclass_name",
        "selectedPrice" to "selected_price",
        "commodityPrice" to "commodity_price",
        "selectedQuantity" to "selected_quantity",
        "commodityQuantity" to "commodity_quantity",
        "saleRate" to "sale_rate",
        "soldPerDay" to "sold_per_day",
    )

internal val AUCTION_MARKET_LIGHTWEIGHT_SORT_COLUMNS =
    setOf("saleRate", "soldPerDay", "selectedPrice", "commodityPrice", "selectedQuantity", "commodityQuantity")
