import { inject, Injectable } from '@angular/core';
import { Observable, of, tap } from 'rxjs';

import {
  AuctionMarketApiService,
  AuctionMarketItemCraftingAnalyticsResponse,
  AuctionMarketItemDetailResponse,
} from '@api/generated';

import { MarketBrowserCache } from './market-browser.cache';
import { RealmSelectionService } from './realm-selection.service';
import { LocaleService } from './locale.service';

export interface ItemDetailVariantParams {
  readonly bonusKey: string;
  readonly modifierKey: string;
  readonly petSpeciesId: number;
}
export type ItemDetailScope = 'realm' | 'commodity';

@Injectable({
  providedIn: 'root',
})
export class MarketItemDetailService {
  private readonly auctionMarketApi = inject(AuctionMarketApiService);
  private readonly marketBrowserCache = inject(MarketBrowserCache);
  private readonly realmSelection = inject(RealmSelectionService);
  private readonly locale = inject(LocaleService);

  loadItemDetail(
    region: 'us' | 'eu' | 'kr' | 'tw',
    realmSlug: string,
    itemId: number,
    variant: ItemDetailVariantParams,
    scope: ItemDetailScope,
    locale?: string,
    preferredRecipeId?: number,
  ): Observable<AuctionMarketItemDetailResponse> {
    const effectiveLocale = locale ?? this.locale.apiLocaleOverride();
    const localeCacheKey = locale ?? this.locale.dataLocaleCacheKey();
    const routeKey = `${region}:${realmSlug.toLowerCase()}`;
    const variantKey = `${variant.bonusKey}|${variant.modifierKey}|${variant.petSpeciesId}`;
    const localeKey = localeCacheKey.trim() ? `:${localeCacheKey.trim()}` : '';
    const recipeKey = preferredRecipeId ? `:recipe:${preferredRecipeId}` : '';
    const detailKey = `${routeKey}:item:${itemId}:v:${variantKey}:scope:${scope}${localeKey}${recipeKey}`;
    const version = this.realmSelection.marketDataVersion();

    const cached = version ? this.marketBrowserCache.getItemDetail(detailKey, version) : undefined;
    if (cached) {
      return of(cached);
    }

    return this.auctionMarketApi
      .getAuctionMarketItemDetail(
        region,
        realmSlug,
        itemId,
        variant.bonusKey,
        variant.modifierKey,
        variant.petSpeciesId,
        scope,
        preferredRecipeId,
        effectiveLocale,
        'body',
        false,
        { transferCache: false },
      )
      .pipe(
        tap((response) => {
          if (version && this.realmSelection.marketDataVersion() === version) {
            this.marketBrowserCache.setItemDetail(detailKey, version, response);
          }
        }),
      );
  }

  loadCraftingAnalytics(
    region: 'us' | 'eu' | 'kr' | 'tw',
    realmSlug: string,
    itemId: number,
    recipeId: number,
    variant: ItemDetailVariantParams,
    locale?: string,
  ): Observable<AuctionMarketItemCraftingAnalyticsResponse> {
    const effectiveLocale = locale ?? this.locale.apiLocaleOverride();
    const localeCacheKey = locale ?? this.locale.dataLocaleCacheKey();
    const routeKey = `${region}:${realmSlug.toLowerCase()}`;
    const variantKey = `${variant.bonusKey}|${variant.modifierKey}|${variant.petSpeciesId}`;
    const localeKey = localeCacheKey.trim() ? `:${localeCacheKey.trim()}` : '';
    const analyticsKey = `${routeKey}:item:${itemId}:recipe:${recipeId}:v:${variantKey}${localeKey}`;
    const version = this.realmSelection.marketDataVersion();

    const cached = version
      ? this.marketBrowserCache.getCraftingAnalytics(analyticsKey, version)
      : undefined;
    if (cached) {
      return of(cached);
    }

    return this.auctionMarketApi
      .getAuctionMarketItemCraftingAnalytics(
        region,
        realmSlug,
        itemId,
        recipeId,
        variant.bonusKey,
        variant.modifierKey,
        variant.petSpeciesId,
        effectiveLocale,
        'body',
        false,
        { transferCache: false },
      )
      .pipe(
        tap((response) => {
          if (version && this.realmSelection.marketDataVersion() === version) {
            this.marketBrowserCache.setCraftingAnalytics(analyticsKey, version, response);
          }
        }),
      );
  }
}
