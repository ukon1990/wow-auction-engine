package net.jonasmf.auctionengine.auction.repository

import java.time.LocalDate

internal fun AuctionItemDetailRepository.buildCurrentListingsSqlAndArgs(connectedRealmId: Int, itemId: Int, variant: Boolean, bonusKey: String, modifierKey: String, petSpeciesId: Int): Pair<String, Array<Any?>> =
    AuctionItemDetailSql.buildCurrentListingsSqlAndArgs(connectedRealmId, itemId, variant, bonusKey, modifierKey, petSpeciesId)

internal fun AuctionItemDetailRepository.buildDailySqlAndArgs(connectedRealmId: Int, itemId: Int, fromDate: LocalDate, toDate: LocalDate, variant: Boolean, bonusKey: String, modifierKey: String, petSpeciesId: Int): Pair<String, Array<Any?>> =
    AuctionItemDetailSql.buildDailySqlAndArgs(connectedRealmId, itemId, fromDate, toDate, variant, bonusKey, modifierKey, petSpeciesId)

internal fun AuctionItemDetailRepository.buildHourlySqlAndArgs(connectedRealmId: Int, itemId: Int, fromDate: LocalDate, toDate: LocalDate, variant: Boolean, bonusKey: String, modifierKey: String, petSpeciesId: Int): Pair<String, Array<Any?>> =
    AuctionItemDetailSql.buildHourlySqlAndArgs(connectedRealmId, itemId, fromDate, toDate, variant, bonusKey, modifierKey, petSpeciesId)

internal fun AuctionItemDetailRepository.buildPieSqlAndArgs(connectedRealmId: Int, itemId: Int, statDate: LocalDate, variant: Boolean, bonusKey: String, modifierKey: String, petSpeciesId: Int): Pair<String, Array<Any?>> =
    AuctionItemDetailSql.buildPieSqlAndArgs(connectedRealmId, itemId, statDate, variant, bonusKey, modifierKey, petSpeciesId)

internal fun AuctionItemDetailRepository.buildCraftingSqlAndArgs(connectedRealmId: Int, commodityConnectedRealmId: Int, itemId: Int, statDate: LocalDate, commodityStatDate: LocalDate, hourOfDay: Int, commodityHourOfDay: Int, variant: Boolean, bonusKey: String, modifierKey: String, petSpeciesId: Int, preferredRecipeId: Int?, localeColumnSuffix: String): Pair<String, Array<Any?>> =
    AuctionItemDetailSql.buildCraftingSqlAndArgs(connectedRealmId, commodityConnectedRealmId, itemId, statDate, commodityStatDate, hourOfDay, commodityHourOfDay, variant, bonusKey, modifierKey, petSpeciesId, preferredRecipeId, localeColumnSuffix)
