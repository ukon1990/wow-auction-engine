package net.jonasmf.auctionengine.repository.rds

import java.time.LocalDate

internal fun AuctionMarketItemDetailRepository.buildCurrentListingsSqlAndArgs(connectedRealmId: Int, itemId: Int, variant: Boolean, bonusKey: String, modifierKey: String, petSpeciesId: Int): Pair<String, Array<Any?>> =
    AuctionMarketItemDetailSql.buildCurrentListingsSqlAndArgs(connectedRealmId, itemId, variant, bonusKey, modifierKey, petSpeciesId)

internal fun AuctionMarketItemDetailRepository.buildDailySqlAndArgs(connectedRealmId: Int, itemId: Int, fromDate: LocalDate, toDate: LocalDate, variant: Boolean, bonusKey: String, modifierKey: String, petSpeciesId: Int): Pair<String, Array<Any?>> =
    AuctionMarketItemDetailSql.buildDailySqlAndArgs(connectedRealmId, itemId, fromDate, toDate, variant, bonusKey, modifierKey, petSpeciesId)

internal fun AuctionMarketItemDetailRepository.buildHourlySqlAndArgs(connectedRealmId: Int, itemId: Int, fromDate: LocalDate, toDate: LocalDate, variant: Boolean, bonusKey: String, modifierKey: String, petSpeciesId: Int): Pair<String, Array<Any?>> =
    AuctionMarketItemDetailSql.buildHourlySqlAndArgs(connectedRealmId, itemId, fromDate, toDate, variant, bonusKey, modifierKey, petSpeciesId)

internal fun AuctionMarketItemDetailRepository.buildPieSqlAndArgs(connectedRealmId: Int, itemId: Int, statDate: LocalDate, variant: Boolean, bonusKey: String, modifierKey: String, petSpeciesId: Int): Pair<String, Array<Any?>> =
    AuctionMarketItemDetailSql.buildPieSqlAndArgs(connectedRealmId, itemId, statDate, variant, bonusKey, modifierKey, petSpeciesId)

internal fun AuctionMarketItemDetailRepository.buildCraftingSqlAndArgs(connectedRealmId: Int, commodityConnectedRealmId: Int, itemId: Int, statDate: LocalDate, commodityStatDate: LocalDate, hourOfDay: Int, commodityHourOfDay: Int, variant: Boolean, bonusKey: String, modifierKey: String, petSpeciesId: Int, preferredRecipeId: Int?, localeColumnSuffix: String): Pair<String, Array<Any?>> =
    AuctionMarketItemDetailSql.buildCraftingSqlAndArgs(connectedRealmId, commodityConnectedRealmId, itemId, statDate, commodityStatDate, hourOfDay, commodityHourOfDay, variant, bonusKey, modifierKey, petSpeciesId, preferredRecipeId, localeColumnSuffix)
