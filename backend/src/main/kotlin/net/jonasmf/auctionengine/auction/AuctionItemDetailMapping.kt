package net.jonasmf.auctionengine.auction

import net.jonasmf.auctionengine.generated.model.AuctionMarketItem
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemCurrentListing
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemDetailPoint
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemHourlyPoint
import net.jonasmf.auctionengine.generated.model.AuctionMarketNamedId
import net.jonasmf.auctionengine.generated.model.AuctionMarketQuantityPieSlice
import net.jonasmf.auctionengine.generated.model.AuctionMarketRecipe
import net.jonasmf.auctionengine.auction.repository.AuctionItemCurrentListingRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemDetailDailyRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemDetailHourlyRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemDetailPieRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemHeaderRow

internal fun AuctionItemHeaderRow.toAuctionMarketItem(): AuctionMarketItem =
    AuctionMarketItem(
        id = itemId,
        name = itemName,
        mediaUrl = itemMediaUrl,
        quality = qualityId?.let { AuctionMarketNamedId(it, qualityName.orEmpty(), qualityType) },
        itemClass = itemClassId?.let { AuctionMarketNamedId(it, itemClassName.orEmpty()) },
        itemSubclass = itemSubclassId?.let { AuctionMarketNamedId(it, itemSubclassName.orEmpty()) },
        expansion = expansionId?.let { AuctionMarketNamedId(it, expansionName.orEmpty()) },
        recipe = recipeId?.let { AuctionMarketRecipe(id = it, name = recipeName.orEmpty(), mediaUrl = recipeMediaUrl, rank = recipeRank) },
    )

internal fun AuctionItemDetailDailyRow.toDetailPoint(): AuctionMarketItemDetailPoint =
    AuctionMarketItemDetailPoint(
        statDate = statDate, pointTimestamp = pointTimestamp, minPrice = minPrice, avgPrice = avgPrice,
        p25Price = p25Price, p75Price = p75Price, maxPrice = maxPrice, minQuantity = minQuantity,
        avgQuantity = avgQuantity, maxQuantity = maxQuantity,
    )

internal fun AuctionItemDetailHourlyRow.toHourlyPoint(): AuctionMarketItemHourlyPoint =
    AuctionMarketItemHourlyPoint(
        timestamp = timestamp, hourOfDay = hourOfDay, minPrice = minPrice, avgPrice = avgPrice,
        p25Price = p25Price, p75Price = p75Price, maxPrice = maxPrice, totalQuantity = totalQuantity,
    )

internal fun AuctionItemDetailPieRow.toPieSlice(): AuctionMarketQuantityPieSlice =
    AuctionMarketQuantityPieSlice(hourOfDay = hourOfDay, fraction = fraction, quantity = quantity)

internal fun AuctionItemCurrentListingRow.toCurrentListingDto(): AuctionMarketItemCurrentListing =
    AuctionMarketItemCurrentListing(price = price, quantity = quantity)
