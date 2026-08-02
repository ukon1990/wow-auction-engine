package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.generated.model.AuctionMarketItem
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemCurrentListing
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemDetailPoint
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemHourlyPoint
import net.jonasmf.auctionengine.generated.model.AuctionMarketNamedId
import net.jonasmf.auctionengine.generated.model.AuctionMarketQuantityPieSlice
import net.jonasmf.auctionengine.generated.model.AuctionMarketRecipe
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemCurrentListingRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemDetailDailyRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemDetailHourlyRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemDetailPieRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemHeaderRow

internal fun AuctionMarketItemHeaderRow.toAuctionMarketItem(): AuctionMarketItem =
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

internal fun AuctionMarketItemDetailDailyRow.toDetailPoint(): AuctionMarketItemDetailPoint =
    AuctionMarketItemDetailPoint(
        statDate = statDate, pointTimestamp = pointTimestamp, minPrice = minPrice, avgPrice = avgPrice,
        p25Price = p25Price, p75Price = p75Price, maxPrice = maxPrice, minQuantity = minQuantity,
        avgQuantity = avgQuantity, maxQuantity = maxQuantity,
    )

internal fun AuctionMarketItemDetailHourlyRow.toHourlyPoint(): AuctionMarketItemHourlyPoint =
    AuctionMarketItemHourlyPoint(
        timestamp = timestamp, hourOfDay = hourOfDay, minPrice = minPrice, avgPrice = avgPrice,
        p25Price = p25Price, p75Price = p75Price, maxPrice = maxPrice, totalQuantity = totalQuantity,
    )

internal fun AuctionMarketItemDetailPieRow.toPieSlice(): AuctionMarketQuantityPieSlice =
    AuctionMarketQuantityPieSlice(hourOfDay = hourOfDay, fraction = fraction, quantity = quantity)

internal fun AuctionMarketItemCurrentListingRow.toCurrentListingDto(): AuctionMarketItemCurrentListing =
    AuctionMarketItemCurrentListing(price = price, quantity = quantity)
