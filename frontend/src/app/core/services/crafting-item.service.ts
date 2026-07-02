import { computed, effect, Injectable, untracked } from '@angular/core';
import { defaultCraftingBrowserQueryState } from '@core/mappers/crafting-browser-query.mapper';
import { toCraftingFilterSections } from '@core/mappers/crafting-filter-mapper';
import { toCraftingRow } from '@core/mappers/crafting-mapper';
import { CraftingBrowserQueryState } from '@core/models/crafting-browser.models';
import { Region } from '@core/services/query.service';
import { applyCraftingFilterToggle, applyCraftingRangeFilter } from '@core/utils/filter';
import {
  AuctionMarketFilter,
  CraftingMarketApiService,
  CraftingMarketSearchPage,
} from '@api/generated';
import { FilterSection } from '@ui';
import { firstValueFrom } from 'rxjs';

import { BaseSearchService } from './base-search.service';

@Injectable()
export class CraftingItemService extends BaseSearchService<
  CraftingMarketSearchPage,
  never,
  never,
  CraftingBrowserQueryState
> {
  readonly currentRows = computed(() => {
    const page = this.pageData();
    if (!page?.items) return [];
    return page.items.map(toCraftingRow);
  });

  private readonly _searchEffect = effect(() => {
    const filter = this.queryParams();
    const region = this.queryService.region();
    const slug = this.queryService.realmSlug();
    const locale = this.queryService.locale();
    if (!filter || !region || !slug || !locale) return;
    untracked(() => firstValueFrom(this.getPageByQuery(locale, region, slug, filter)));
  });

  constructor(private api: CraftingMarketApiService) {
    super(
      defaultCraftingBrowserQueryState,
      (region: 'us' | 'eu' | 'kr' | 'tw', realmSlug: string, locale?: string) =>
        api.filters(region, realmSlug, locale, 'body', false),
    );
  }

  toggleFilter(optionId: string): void {
    this.navigateQueryState({
      ...applyCraftingFilterToggle(this.currentQueryState(), optionId),
      page: defaultCraftingBrowserQueryState.page,
    });
  }

  setRangeFilter(sectionId: string, bound: 'min' | 'max', value: number | null): void {
    this.navigateQueryState({
      ...applyCraftingRangeFilter(this.currentQueryState(), sectionId, bound, value),
      page: defaultCraftingBrowserQueryState.page,
    });
  }

  override resetFilters(): void {
    const pageSize = this.currentQueryState().pageSize;
    this.navigateQueryState({ ...defaultCraftingBrowserQueryState, pageSize });
  }

  protected override mapFiltersToSections(
    filters: readonly AuctionMarketFilter[],
    state: CraftingBrowserQueryState,
  ): readonly FilterSection[] {
    return toCraftingFilterSections(filters, state);
  }

  getPageByQuery(
    locale: string,
    region: Region,
    slug: string,
    queryParams: CraftingBrowserQueryState,
  ) {
    const {
      page,
      pageSize,
      sortBy,
      sortDirection,
      query,
      professionIds,
      expansionIds,
      qualityIds,
      minProfit,
      maxProfit,
      minRoiPercent,
      maxRoiPercent,
      minReagentCost,
      maxReagentCost,
      minOutputPrice,
      maxOutputPrice,
      minOutputPriceChangePercent,
      maxOutputPriceChangePercent,
      requireCompleteReagentPricing,
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
        professionIds.length ? [...professionIds] : undefined,
        expansionIds.length ? [...expansionIds] : undefined,
        qualityIds.length ? [...qualityIds] : undefined,
        minProfit ?? undefined,
        maxProfit ?? undefined,
        minRoiPercent ?? undefined,
        maxRoiPercent ?? undefined,
        minReagentCost ?? undefined,
        maxReagentCost ?? undefined,
        minOutputPrice ?? undefined,
        maxOutputPrice ?? undefined,
        minOutputPriceChangePercent ?? undefined,
        maxOutputPriceChangePercent ?? undefined,
        requireCompleteReagentPricing ? true : undefined,
        'body',
        false,
        { transferCache: false },
      ),
    );
  }
}
