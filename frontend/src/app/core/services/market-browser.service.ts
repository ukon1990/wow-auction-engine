import { inject, Injectable, signal } from '@angular/core';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { DecimalPipe } from '@angular/common';

import {
  copperToCurrencyAmount,
  FilterSection,
  ItemQuality,
  MarketItemRow,
  type SortingState,
} from '@ui';
import {
  AuctionMarketApiService,
  AuctionMarketFilter,
  AuctionMarketSearchPage,
  AuctionMarketSearchRow,
} from '@api/generated';
import { MarketBrowserViewModel } from '../models/market-browser.models';
import { MarketBrowserCache } from './market-browser.cache';
import { RealmSelectionService } from './realm-selection.service';

interface MarketBrowserQueryState {
  readonly query: string;
  readonly qualityIds: readonly number[];
  readonly itemClassIds: readonly number[];
  readonly itemSubclassIds: readonly number[];
  readonly recipeOnly: boolean | null;
  readonly minPrice: number | null;
  readonly maxPrice: number | null;
  readonly minQuantity: number | null;
  readonly maxQuantity: number | null;
  readonly page: number;
  readonly pageSize: number;
  readonly sortBy:
    | 'itemName'
    | 'quality'
    | 'itemClass'
    | 'itemSubclass'
    | 'selectedPrice'
    | 'commodityPrice'
    | 'selectedQuantity'
    | 'commodityQuantity';
  readonly sortDirection: 'asc' | 'desc';
}

const defaultQueryState: MarketBrowserQueryState = {
  query: '',
  qualityIds: [],
  itemClassIds: [],
  itemSubclassIds: [],
  recipeOnly: null,
  minPrice: null,
  maxPrice: null,
  minQuantity: null,
  maxQuantity: null,
  page: 0,
  pageSize: 25,
  sortBy: 'itemName',
  sortDirection: 'asc',
};

@Injectable({
  providedIn: 'root',
})
export class MarketBrowserService {
  private readonly auctionMarketApi = inject(AuctionMarketApiService);
  private readonly router = inject(Router);
  private readonly realmSelection = inject(RealmSelectionService);
  private readonly marketBrowserCache = inject(MarketBrowserCache);
  private readonly decimalPipe = new DecimalPipe('en-US');
  private route: ActivatedRoute | null = null;
  private filterRequestId = 0;
  private searchRequestId = 0;
  private queryState: MarketBrowserQueryState = defaultQueryState;
  private routeRegion: 'us' | 'eu' | 'kr' | 'tw' | null = null;
  private routeRealmSlug: string | null = null;

  private readonly marketBrowser = signal<MarketBrowserViewModel>({
    primaryNavItems: [
      // TODO: remove
    ],
    activePrimaryNavId: 'market-browser',
    professionNavItems: [
      { id: 'alchemy', label: 'Alchemy', icon: 'water_medium' },
      { id: 'blacksmithing', label: 'Blacksmithing', icon: 'swords' },
      { id: 'enchanting', label: 'Enchanting', icon: 'magic_button' },
      { id: 'jewelcrafting', label: 'Jewelcrafting', icon: 'diamond' },
      { id: 'inscription', label: 'Inscription', icon: 'auto_stories' },
    ],
    activeProfessionId: 'blacksmithing',
    character: {
      name: 'GoblinKing99',
      realm: 'Illidan-US',
      level: 70,
      profession: 'Blacksmithing',
      skill: 'Skill Level 300/300',
    },
    filterSections: [],
    rows: [],
    paginationSummary: 'Loading market items...',
    searchQuery: '',
    page: 0,
    totalPages: 0,
    pageSize: defaultQueryState.pageSize,
    sortBy: defaultQueryState.sortBy,
    sortDirection: defaultQueryState.sortDirection,
    loading: false,
  });

  readonly viewModel = this.marketBrowser.asReadonly();

  bindRoute(route: ActivatedRoute): void {
    this.route = route;
  }

  loadFromRoute(paramMap: ParamMap, queryParamMap: ParamMap): void {
    const region = paramMap.get('region')?.toLowerCase();
    const realmSlug = paramMap.get('realm');
    if (!isRegion(region) || !realmSlug) return;
    this.routeRegion = region;
    this.routeRealmSlug = realmSlug;
    this.queryState = readQueryState(queryParamMap);

    const filterReqId = ++this.filterRequestId;
    const searchReqId = ++this.searchRequestId;

    const routeKey = `${region}:${realmSlug.toLowerCase()}`;
    const queryString = stableQueryStringFromState(this.queryState);
    const searchKey = `${routeKey}:${queryString}`;
    const version = this.realmSelection.marketDataVersion();

    const cachedFilterEnvelope = version
      ? this.marketBrowserCache.getFilters(routeKey, version)
      : undefined;

    if (cachedFilterEnvelope) {
      const filters = cachedFilterEnvelope.filters ?? [];
      this.marketBrowser.update((vm) => ({
        ...vm,
        searchQuery: this.queryState.query,
        page: this.queryState.page,
        pageSize: this.queryState.pageSize,
        sortBy: this.queryState.sortBy,
        sortDirection: this.queryState.sortDirection,
        loading: true,
        paginationSummary: 'Loading...',
        filterSections: toFilterSections(filters, this.queryState),
      }));
    } else {
      this.marketBrowser.update((vm) => ({
        ...vm,
        searchQuery: this.queryState.query,
        page: this.queryState.page,
        pageSize: this.queryState.pageSize,
        sortBy: this.queryState.sortBy,
        sortDirection: this.queryState.sortDirection,
        loading: true,
        paginationSummary: 'Loading...',
        filterSections: [],
      }));
      this.auctionMarketApi
        .getAuctionMarketFilters(region, realmSlug, undefined, 'body', false)
        .subscribe({
          next: (response) => {
            if (filterReqId !== this.filterRequestId) return;
            const filters = response.filters ?? [];
            this.marketBrowser.update((vm) => ({
              ...vm,
              pageSize: this.queryState.pageSize,
              sortBy: this.queryState.sortBy,
              sortDirection: this.queryState.sortDirection,
              filterSections: toFilterSections(filters, this.queryState),
            }));
            if (version && this.realmSelection.marketDataVersion() === version) {
              this.marketBrowserCache.setFilters(routeKey, version, response);
            }
          },
          error: () => {
            if (filterReqId !== this.filterRequestId) return;
            this.marketBrowser.update((vm) => ({
              ...vm,
              pageSize: this.queryState.pageSize,
              sortBy: this.queryState.sortBy,
              sortDirection: this.queryState.sortDirection,
              filterSections: [],
            }));
          },
        });
    }

    const cachedSearchPage = version
      ? this.marketBrowserCache.getSearch(searchKey, version)
      : undefined;
    if (cachedSearchPage) {
      if (searchReqId !== this.searchRequestId) return;
      this.applySearchPage(cachedSearchPage);
    } else {
      this.auctionMarketApi
        .searchAuctionMarket(
          region,
          realmSlug,
          undefined,
          this.queryState.page,
          this.queryState.pageSize,
          this.queryState.sortBy,
          this.queryState.sortDirection,
          this.queryState.query || undefined,
          this.queryState.qualityIds.length ? [...this.queryState.qualityIds] : undefined,
          this.queryState.itemClassIds.length ? [...this.queryState.itemClassIds] : undefined,
          this.queryState.itemSubclassIds.length ? [...this.queryState.itemSubclassIds] : undefined,
          this.queryState.recipeOnly ?? undefined,
          this.queryState.minPrice ?? undefined,
          this.queryState.maxPrice ?? undefined,
          this.queryState.minQuantity ?? undefined,
          this.queryState.maxQuantity ?? undefined,
          'body',
          false,
        )
        .subscribe({
          next: (response) => {
            if (searchReqId !== this.searchRequestId) return;
            this.applySearchPage(response);
            if (version && this.realmSelection.marketDataVersion() === version) {
              this.marketBrowserCache.setSearch(searchKey, version, response);
            }
          },
          error: () => {
            if (searchReqId !== this.searchRequestId) return;
            this.marketBrowser.update((vm) => ({
              ...vm,
              loading: false,
              rows: [],
              pageSize: this.queryState.pageSize,
              sortBy: this.queryState.sortBy,
              sortDirection: this.queryState.sortDirection,
              paginationSummary: 'No market items available.',
            }));
          },
        });
    }
  }

  setActivePrimaryNavId(id: string): void {
    this.marketBrowser.update((vm) => ({ ...vm, activePrimaryNavId: id }));
  }

  setActiveProfessionId(id: string): void {
    this.marketBrowser.update((vm) => ({ ...vm, activeProfessionId: id }));
  }

  setSearchQuery(query: string): void {
    this.navigateWithState({ ...this.queryState, query, page: 0 });
  }

  toggleFilter(optionId: string): void {
    const [filterId, rawValue] = optionId.split(':');
    if (!filterId || !rawValue) return;
    if (filterId === 'recipeOnly') {
      this.navigateWithState({
        ...this.queryState,
        recipeOnly: this.queryState.recipeOnly === true ? null : true,
        page: 0,
      });
      return;
    }
    const value = Number(rawValue);
    if (!Number.isFinite(value)) return;
    if (filterId === 'qualityIds') {
      this.navigateWithState({
        ...this.queryState,
        qualityIds: toggleNumber(this.queryState.qualityIds, value),
        page: 0,
      });
    }
  }

  selectFilter(filterId: string, optionId: string | null): void {
    const optionIdParts = optionId?.split(':') ?? [];
    const rawValue =
      optionIdParts.length === 0 ? undefined : optionIdParts[optionIdParts.length - 1];
    const value = rawValue === undefined ? null : Number(rawValue);
    if (value !== null && !Number.isFinite(value)) return;
    if (filterId === 'itemClassIds') {
      this.navigateWithState({
        ...this.queryState,
        itemClassIds: value === null ? [] : [value],
        itemSubclassIds: [],
        page: 0,
      });
    } else if (filterId === 'itemSubclassIds') {
      this.navigateWithState({
        ...this.queryState,
        itemSubclassIds: value === null ? [] : [value],
        page: 0,
      });
    }
  }

  setRangeFilter(id: string, bound: 'min' | 'max', value: number | null): void {
    const key =
      id === 'price'
        ? bound === 'min'
          ? 'minPrice'
          : 'maxPrice'
        : id === 'quantity'
          ? bound === 'min'
            ? 'minQuantity'
            : 'maxQuantity'
          : null;
    if (!key) return;
    this.navigateWithState({ ...this.queryState, [key]: value, page: 0 });
  }

  goToPreviousPage(): void {
    if (this.queryState.page <= 0) return;
    this.navigateWithState({ ...this.queryState, page: this.queryState.page - 1 });
  }

  goToNextPage(): void {
    if (this.queryState.page + 1 >= this.marketBrowser().totalPages) return;
    this.navigateWithState({ ...this.queryState, page: this.queryState.page + 1 });
  }

  applyTableSort(sorting: SortingState): void {
    const first = sorting[0];
    const sortBy = first ? readSortBy(first.id) : defaultQueryState.sortBy;
    const sortDirection: 'asc' | 'desc' = first?.desc ? 'desc' : 'asc';
    if (this.queryState.sortBy === sortBy && this.queryState.sortDirection === sortDirection) {
      return;
    }
    this.navigateWithState({ ...this.queryState, sortBy, sortDirection, page: 0 });
  }

  resetFilters(): void {
    this.navigateWithState({ ...defaultQueryState, pageSize: this.queryState.pageSize });
  }

  private applySearchPage(response: AuctionMarketSearchPage): void {
    const totalItems = response.page.totalItems ?? 0;
    const page = response.page.page ?? 0;
    const pageSize = response.page.pageSize ?? this.queryState.pageSize;
    const start = totalItems === 0 ? 0 : page * pageSize + 1;
    const end = Math.min((page + 1) * pageSize, totalItems);
    const locale = normalizeLocaleForNumberPipe(this.realmSelection.selected()?.locale);
    const startLabel = this.formatInteger(start, locale);
    const endLabel = this.formatInteger(end, locale);
    const totalItemsLabel = this.formatInteger(totalItems, locale);
    this.marketBrowser.update((vm) => ({
      ...vm,
      loading: false,
      rows: (response.items ?? []).map(toMarketRow),
      page,
      totalPages: response.page.totalPages ?? 0,
      pageSize,
      sortBy: this.queryState.sortBy,
      sortDirection: this.queryState.sortDirection,
      paginationSummary:
        totalItems === 0
          ? 'No market items available.'
          : `Showing ${startLabel}-${endLabel} of ${totalItemsLabel} items`,
    }));
  }

  private formatInteger(value: number, locale: string | undefined): string {
    return this.decimalPipe.transform(value, '1.0-0', locale) ?? String(value);
  }

  private navigateWithState(state: MarketBrowserQueryState): void {
    if (!this.route || !this.routeRegion || !this.routeRealmSlug) return;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: toQueryParams(state),
      replaceUrl: true,
    });
  }
}

function isRegion(value: string | undefined): value is 'us' | 'eu' | 'kr' | 'tw' {
  return value === 'us' || value === 'eu' || value === 'kr' || value === 'tw';
}

function readQueryState(queryParamMap: ParamMap): MarketBrowserQueryState {
  return {
    ...defaultQueryState,
    query: queryParamMap.get('query') ?? '',
    qualityIds: queryParamMap.getAll('qualityIds').map(Number).filter(Number.isFinite),
    itemClassIds: firstFinite(queryParamMap.getAll('itemClassIds')),
    itemSubclassIds: firstFinite(queryParamMap.getAll('itemSubclassIds')),
    recipeOnly: queryParamMap.get('recipeOnly') === 'true' ? true : null,
    minPrice: nullableNumber(queryParamMap.get('minPrice')),
    maxPrice: nullableNumber(queryParamMap.get('maxPrice')),
    minQuantity: nullableNumber(queryParamMap.get('minQuantity')),
    maxQuantity: nullableNumber(queryParamMap.get('maxQuantity')),
    page: nullableNumber(queryParamMap.get('page')) ?? 0,
    pageSize: nullableNumber(queryParamMap.get('pageSize')) ?? defaultQueryState.pageSize,
    sortBy: readSortBy(queryParamMap.get('sortBy')),
    sortDirection: queryParamMap.get('sortDirection') === 'desc' ? 'desc' : 'asc',
  };
}

function firstFinite(values: readonly string[]): readonly number[] {
  const value = values.map(Number).find(Number.isFinite);
  return value === undefined ? [] : [value];
}

function nullableNumber(value: string | null): number | null {
  if (value === null || value === '') return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function readSortBy(value: string | null): MarketBrowserQueryState['sortBy'] {
  const allowed = [
    'itemName',
    'quality',
    'itemClass',
    'itemSubclass',
    'selectedPrice',
    'commodityPrice',
    'selectedQuantity',
    'commodityQuantity',
  ] as const;
  const raw = allowed.find((candidate) => candidate === value) ?? 'itemName';
  if (raw === 'commodityPrice') return 'selectedPrice';
  if (raw === 'commodityQuantity') return 'selectedQuantity';
  return raw;
}

function stableQueryStringFromState(state: MarketBrowserQueryState): string {
  const params = toQueryParams(state) as Record<
    string,
    string | number | boolean | readonly number[] | null
  >;
  const keys = Object.keys(params).sort();
  const usp = new URLSearchParams();
  for (const key of keys) {
    const v = params[key];
    if (v === null || v === undefined) continue;
    if (Array.isArray(v)) {
      for (const item of [...v].sort((a, b) => a - b)) {
        usp.append(key, String(item));
      }
    } else {
      usp.append(key, String(v));
    }
  }
  return usp.toString();
}

function toQueryParams(
  state: MarketBrowserQueryState,
): Record<string, string | number | boolean | readonly number[] | null> {
  return {
    query: state.query || null,
    qualityIds: state.qualityIds.length ? state.qualityIds : null,
    itemClassIds: state.itemClassIds.length ? state.itemClassIds : null,
    itemSubclassIds: state.itemSubclassIds.length ? state.itemSubclassIds : null,
    recipeOnly: state.recipeOnly,
    minPrice: state.minPrice,
    maxPrice: state.maxPrice,
    minQuantity: state.minQuantity,
    maxQuantity: state.maxQuantity,
    page: state.page === 0 ? null : state.page,
    pageSize: state.pageSize === defaultQueryState.pageSize ? null : state.pageSize,
    sortBy: state.sortBy === 'itemName' ? null : state.sortBy,
    sortDirection: state.sortDirection === 'asc' ? null : state.sortDirection,
  };
}

function toFilterSections(
  filters: readonly AuctionMarketFilter[],
  state: MarketBrowserQueryState,
): readonly FilterSection[] {
  return filters.map((filter) => {
    const selectedIds = selectedSet(filter.id, state);
    if (filter.type === AuctionMarketFilter.TypeEnum.Boolean) {
      return {
        id: filter.id,
        label: filter.label,
        type: filter.type,
        options: [
          { id: `${filter.id}:true`, label: filter.label, selected: state.recipeOnly === true },
        ],
      };
    }
    return {
      id: filter.id,
      label: filter.label,
      type: filterType(filter),
      min: filter.min ?? undefined,
      max: filter.max ?? undefined,
      selectedMin: selectedRangeValue(filter.id, 'min', state),
      selectedMax: selectedRangeValue(filter.id, 'max', state),
      options: filterOptions(filter, state).map((option) => ({
        id: filterOptionId(filter.id, option),
        label: option.label,
        selected: selectedIds.has(option.id),
        parentId: option.parentId ?? undefined,
        quality: filter.id === 'qualityIds' ? toQuality(option.label) : undefined,
      })),
    };
  });
}

function filterType(filter: AuctionMarketFilter): FilterSection['type'] {
  if (filter.id === 'itemClassIds' || filter.id === 'itemSubclassIds') return 'select';
  return filter.type;
}

function filterOptions(
  filter: AuctionMarketFilter,
  state: MarketBrowserQueryState,
): readonly NonNullable<AuctionMarketFilter['options']>[number][] {
  const options = filter.options ?? [];
  if (filter.id !== 'itemSubclassIds' || state.itemClassIds.length === 0) return options;
  const selectedClassIds = new Set(state.itemClassIds.map(String));
  return options.filter(
    (option) => option.parentId !== null && selectedClassIds.has(String(option.parentId)),
  );
}

function filterOptionId(
  filterId: string,
  option: NonNullable<AuctionMarketFilter['options']>[number],
): string {
  if (filterId === 'itemSubclassIds' && option.parentId !== null && option.parentId !== undefined) {
    return `${filterId}:${option.parentId}:${option.id}`;
  }
  return `${filterId}:${option.id}`;
}

function selectedSet(filterId: string, state: MarketBrowserQueryState): Set<string> {
  if (filterId === 'qualityIds') return new Set(state.qualityIds.map(String));
  if (filterId === 'itemClassIds') return new Set(state.itemClassIds.map(String));
  if (filterId === 'itemSubclassIds') return new Set(state.itemSubclassIds.map(String));
  return new Set();
}

function selectedRangeValue(
  filterId: string,
  bound: 'min' | 'max',
  state: MarketBrowserQueryState,
): number | undefined {
  if (filterId === 'price') return (bound === 'min' ? state.minPrice : state.maxPrice) ?? undefined;
  if (filterId === 'quantity')
    return (bound === 'min' ? state.minQuantity : state.maxQuantity) ?? undefined;
  return undefined;
}

function nonemptyName(value: string | null | undefined): string | undefined {
  const t = value?.trim();
  return t && t.length > 0 ? t : undefined;
}

function toMarketRow(row: AuctionMarketSearchRow): MarketItemRow {
  const listingPriceCopper = row.selectedRealm?.price ?? row.commodity?.price;
  const listingQuantity = row.selectedRealm?.quantity ?? row.commodity?.quantity;
  const mergedCurrency = copperToCurrencyAmount(listingPriceCopper);
  const preferredScope = readPreferredScope(row);
  return {
    id: String(row.item.id),
    name: row.item.name,
    preferredScope,
    listingKey: row.listingKey
      ? {
          bonusKey: row.listingKey.bonusKey,
          modifierKey: row.listingKey.modifierKey,
          petSpeciesId: row.listingKey.petSpeciesId,
        }
      : undefined,
    itemClassName: nonemptyName(row.item.itemClass?.name),
    itemSubclassName: nonemptyName(row.item.itemSubclass?.name),
    quality: toQuality(row.item.quality?.type ?? row.item.quality?.name),
    iconUrl: row.item.mediaUrl ?? undefined,
    minBuyout: mergedCurrency,
    marketValue: {},
    regionalAverage: mergedCurrency,
    saleRate: 0,
    selectedQuantity: listingQuantity ?? undefined,
  };
}

function readPreferredScope(row: AuctionMarketSearchRow): 'realm' | 'commodity' | undefined {
  const raw = row.preferredScope;
  return raw === 'commodity' || raw === 'realm' ? raw : undefined;
}

function toQuality(value: string | undefined): ItemQuality {
  const normalized = value?.toLowerCase();
  if (
    normalized === 'common' ||
    normalized === 'uncommon' ||
    normalized === 'rare' ||
    normalized === 'epic' ||
    normalized === 'legendary'
  ) {
    return normalized;
  }
  return 'common';
}

function toggleNumber(values: readonly number[], value: number): readonly number[] {
  return values.includes(value)
    ? values.filter((candidate) => candidate !== value)
    : [...values, value];
}

function normalizeLocaleForNumberPipe(locale: string | undefined): string | undefined {
  return locale?.replace('_', '-');
}
