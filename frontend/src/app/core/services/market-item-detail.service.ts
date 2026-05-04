import { inject, Injectable } from '@angular/core';
import { Observable, of, tap } from 'rxjs';

import { AuctionMarketApiService, AuctionMarketItemDetailResponse } from '@api/generated';

import { MarketBrowserCache } from './market-browser.cache';
import { RealmSelectionService } from './realm-selection.service';

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

  loadItemDetail(
    region: 'us' | 'eu' | 'kr' | 'tw',
    realmSlug: string,
    itemId: number,
    variant: ItemDetailVariantParams,
    scope: ItemDetailScope,
    locale?: string,
  ): Observable<AuctionMarketItemDetailResponse> {
    const routeKey = `${region}:${realmSlug.toLowerCase()}`;
    const variantKey = `${variant.bonusKey}|${variant.modifierKey}|${variant.petSpeciesId}`;
    const localeKey = locale?.trim() ? `:${locale.trim()}` : '';
    const detailKey = `${routeKey}:item:${itemId}:v:${variantKey}:scope:${scope}${localeKey}`;
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
        locale,
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
}
