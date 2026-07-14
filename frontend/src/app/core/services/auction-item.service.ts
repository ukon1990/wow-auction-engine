import { computed, effect, Injectable, untracked } from '@angular/core';
import { defaultMarketBrowserQueryState } from '@core/mappers/market-browser-query.mapper';
import { BaseSearchService } from './base-search.service';
import { AuctionMarketApiService, AuctionMarketSearchPage } from '@api/generated';
import { MarketBrowserQueryState } from '@core/models/market-browser.models';
import { toMarketRow } from '@core/mappers/auction-mapper';
import { Region } from '@core/services/query.service';
import {
  applyMarketFilterSelect,
  applyMarketFilterToggle,
  applyMarketRangeFilter,
} from '@core/utils/filter';
import { firstValueFrom } from 'rxjs';

@Injectable() // AuctionMarketSearchPage,
export class AuctionItemService extends BaseSearchService<
  AuctionMarketSearchPage,
  never,
  never,
  MarketBrowserQueryState
> {
  readonly currentRows = computed(() => {
    const page = this.pageData();
    if (!page) return [];
    return page.items.map(toMarketRow);
  });

  private readonly _searchEffect = effect(() => {
    const filter = this.queryParams();
    const region = this.queryService.region();
    const slug = this.queryService.realmSlug();
    const locale = this.queryService.locale();
    if (!filter || !region || !slug || !locale) return;
    untracked(() => firstValueFrom(this.getPageByQuery(locale, region, slug, filter)));
  });

  constructor(private api: AuctionMarketApiService) {
    super(
      defaultMarketBrowserQueryState,
      (region: 'us' | 'eu' | 'kr' | 'tw', realmSlug: string, locale?: string) =>
        api.filters(region, realmSlug, locale),
    );
  }

  toggleFilter(optionId: string): void {
    this.navigateQueryState({
      ...applyMarketFilterToggle(this.currentQueryState(), optionId),
      page: defaultMarketBrowserQueryState.page,
    });
  }

  selectFilter(sectionId: string, optionId: string | null): void {
    this.navigateQueryState({
      ...applyMarketFilterSelect(this.currentQueryState(), sectionId, optionId),
      page: defaultMarketBrowserQueryState.page,
    });
  }

  setRangeFilter(sectionId: string, bound: 'min' | 'max', value: number | null): void {
    this.navigateQueryState({
      ...applyMarketRangeFilter(this.currentQueryState(), sectionId, bound, value),
      page: defaultMarketBrowserQueryState.page,
    });
  }

  getPageByQuery(
    locale: string,
    region: Region,
    slug: string,
    queryParams: MarketBrowserQueryState,
  ) {
    const {
      page,
      pageSize,
      sortBy,
      sortDirection,
      query,
      qualityIds,
      itemClassIds,
      itemSubclassIds,
      expansionIds,
      minPrice,
      maxPrice,
      minQuantity,
      maxQuantity,
      minSaleRatePercent,
      maxSaleRatePercent,
      minSoldPerDay,
      maxSoldPerDay,
      recipeOnly,
    } = queryParams;

    return super.search(queryParams, () =>
      this.api.search(
        region,
        slug,
        locale,
        page,
        pageSize,
        sortBy,
        sortDirection,
        query || undefined,
        qualityIds.length ? [...qualityIds] : undefined,
        itemClassIds.length ? [...itemClassIds] : undefined,
        itemSubclassIds.length ? [...itemSubclassIds] : undefined,
        expansionIds.length ? [...expansionIds] : undefined,
        recipeOnly ?? undefined,
        minPrice ?? undefined,
        maxPrice ?? undefined,
        minQuantity ?? undefined,
        maxQuantity ?? undefined,
        minSaleRatePercent ?? undefined,
        maxSaleRatePercent ?? undefined,
        minSoldPerDay ?? undefined,
        maxSoldPerDay ?? undefined,
        'body',
        false,
        { transferCache: false },
      ),
    );
  }
}
