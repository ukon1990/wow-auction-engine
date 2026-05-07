import { inject, Injectable, signal } from '@angular/core';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { DecimalPipe } from '@angular/common';

import { FilterSection, type SortingState } from '@ui';
import {
  AuctionMarketApiService,
  AuctionMarketFilter,
  CraftingMarketSearchPage,
  CraftingMarketSearchRow,
} from '@api/generated';
import { LocaleService } from './locale.service';
import type {
  CraftingBrowserQueryState,
  CraftingBrowserViewModel,
  CraftingSortBy,
  CraftingTableRow,
} from '../../features/crafting/crafting-browser.models';

const defaultQueryState: CraftingBrowserQueryState = {
  query: '',
  professionIds: [],
  minProfit: null,
  maxProfit: null,
  minRoiPercent: null,
  maxRoiPercent: null,
  minReagentCost: null,
  maxReagentCost: null,
  minOutputPrice: null,
  maxOutputPrice: null,
  minOutputPriceChangePercent: null,
  maxOutputPriceChangePercent: null,
  requireCompleteReagentPricing: false,
  page: 0,
  pageSize: 25,
  sortBy: 'itemName',
  sortDirection: 'asc',
};

@Injectable({
  providedIn: 'root',
})
export class CraftingBrowserService {
  private readonly auctionMarketApi = inject(AuctionMarketApiService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly decimalPipe = new DecimalPipe(this.locale.formatLocale());
  private route: ActivatedRoute | null = null;
  private filterRequestId = 0;
  private searchRequestId = 0;
  private queryState: CraftingBrowserQueryState = defaultQueryState;
  private routeRegion: 'us' | 'eu' | 'kr' | 'tw' | null = null;
  private routeRealmSlug: string | null = null;

  private readonly vm = signal<CraftingBrowserViewModel>({
    filterSections: [],
    rows: [],
    paginationSummary: $localize`:@@crafting.loadingRecipes:Loading recipes...`,
    searchQuery: '',
    page: 0,
    totalPages: 0,
    pageSize: defaultQueryState.pageSize,
    sortBy: defaultQueryState.sortBy,
    sortDirection: defaultQueryState.sortDirection,
    loading: true,
  });

  readonly viewModel = this.vm.asReadonly();

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

    this.vm.update((v) => ({
      ...v,
      searchQuery: this.queryState.query,
      page: this.queryState.page,
      pageSize: this.queryState.pageSize,
      sortBy: this.queryState.sortBy,
      sortDirection: this.queryState.sortDirection,
      loading: true,
      paginationSummary: $localize`:@@common.loading:Loading...`,
    }));

    const apiLocale = this.locale.apiLocaleOverride();

    this.auctionMarketApi
      .getCraftingMarketFilters(region, realmSlug, apiLocale, 'body', false)
      .subscribe({
        next: (response) => {
          if (filterReqId !== this.filterRequestId) return;
          const filters = response.filters ?? [];
          this.vm.update((v) => ({
            ...v,
            filterSections: toFilterSections(filters, this.queryState),
          }));
        },
        error: () => {
          if (filterReqId !== this.filterRequestId) return;
          this.vm.update((v) => ({ ...v, filterSections: [] }));
        },
      });

    this.auctionMarketApi
      .searchCraftingMarket(
        region,
        realmSlug,
        apiLocale,
        this.queryState.page,
        this.queryState.pageSize,
        this.queryState.sortBy,
        this.queryState.sortDirection,
        this.queryState.query || undefined,
        this.queryState.professionIds.length ? [...this.queryState.professionIds] : undefined,
        this.queryState.minProfit ?? undefined,
        this.queryState.maxProfit ?? undefined,
        this.queryState.minRoiPercent ?? undefined,
        this.queryState.maxRoiPercent ?? undefined,
        this.queryState.minReagentCost ?? undefined,
        this.queryState.maxReagentCost ?? undefined,
        this.queryState.minOutputPrice ?? undefined,
        this.queryState.maxOutputPrice ?? undefined,
        this.queryState.minOutputPriceChangePercent ?? undefined,
        this.queryState.maxOutputPriceChangePercent ?? undefined,
        this.queryState.requireCompleteReagentPricing ? true : undefined,
        'body',
        false,
        { transferCache: false },
      )
      .subscribe({
        next: (response) => {
          if (searchReqId !== this.searchRequestId) return;
          this.applySearchPage(response);
        },
        error: () => {
          if (searchReqId !== this.searchRequestId) return;
          this.vm.update((v) => ({
            ...v,
            loading: false,
            rows: [],
            paginationSummary: $localize`:@@crafting.pagination.empty:No recipes available.`,
          }));
        },
      });
  }

  setSearchQuery(query: string): void {
    this.navigateWithState({ ...this.queryState, query, page: 0 });
  }

  toggleFilter(optionId: string): void {
    const [filterId, rawValue] = optionId.split(':');
    if (!filterId || !rawValue) return;
    if (filterId === 'requireCompleteReagentPricing') {
      this.navigateWithState({
        ...this.queryState,
        requireCompleteReagentPricing: !this.queryState.requireCompleteReagentPricing,
        page: 0,
      });
      return;
    }
    const value = Number(rawValue);
    if (!Number.isFinite(value)) return;
    if (filterId === 'professionIds') {
      this.navigateWithState({
        ...this.queryState,
        professionIds: toggleNumber(this.queryState.professionIds, value),
        page: 0,
      });
    }
  }

  selectFilter(): void {}

  setRangeFilter(id: string, bound: 'min' | 'max', value: number | null): void {
    const map: Record<string, [keyof CraftingBrowserQueryState, keyof CraftingBrowserQueryState]> =
      {
        profit: ['minProfit', 'maxProfit'],
        roiPercent: ['minRoiPercent', 'maxRoiPercent'],
        reagentCost: ['minReagentCost', 'maxReagentCost'],
        outputPrice: ['minOutputPrice', 'maxOutputPrice'],
        outputPriceChangePercent: ['minOutputPriceChangePercent', 'maxOutputPriceChangePercent'],
      };
    const keys = map[id];
    if (!keys) return;
    const key = bound === 'min' ? keys[0] : keys[1];
    this.navigateWithState({ ...this.queryState, [key]: value, page: 0 });
  }

  resetFilters(): void {
    this.navigateWithState({ ...defaultQueryState, pageSize: this.queryState.pageSize });
  }

  goToPreviousPage(): void {
    if (this.queryState.page <= 0) return;
    this.navigateWithState({ ...this.queryState, page: this.queryState.page - 1 });
  }

  goToNextPage(): void {
    if (this.queryState.page + 1 >= this.vm().totalPages) return;
    this.navigateWithState({ ...this.queryState, page: this.queryState.page + 1 });
  }

  applyTableSort(sorting: SortingState): void {
    const first = sorting[0];
    const sortBy = (
      first ? readSortBy(String(first.id)) : defaultQueryState.sortBy
    ) as CraftingSortBy;
    const sortDirection: 'asc' | 'desc' = first?.desc ? 'desc' : 'asc';
    if (this.queryState.sortBy === sortBy && this.queryState.sortDirection === sortDirection)
      return;
    this.navigateWithState({ ...this.queryState, sortBy, sortDirection, page: 0 });
  }

  private applySearchPage(response: CraftingMarketSearchPage): void {
    const totalItems = response.page?.totalItems ?? 0;
    const page = response.page?.page ?? 0;
    const pageSize = response.page?.pageSize ?? this.queryState.pageSize;
    const start = totalItems === 0 ? 0 : page * pageSize + 1;
    const end = Math.min((page + 1) * pageSize, totalItems);
    const locale = this.locale.formatLocale();
    const startLabel = this.formatInteger(start, locale);
    const endLabel = this.formatInteger(end, locale);
    const totalLabel = this.formatInteger(totalItems, locale);
    this.vm.update((v) => ({
      ...v,
      loading: false,
      rows: (response.items ?? []).map(toCraftingRow),
      page,
      totalPages: response.page?.totalPages ?? 0,
      pageSize,
      sortBy: this.queryState.sortBy,
      sortDirection: this.queryState.sortDirection,
      paginationSummary:
        totalItems === 0
          ? $localize`:@@crafting.pagination.empty:No recipes available.`
          : $localize`:@@crafting.pagination.summary:Showing ${startLabel}-${endLabel} of ${totalLabel} rows`,
    }));
  }

  private formatInteger(value: number, locale: string | undefined): string {
    return this.decimalPipe.transform(value, '1.0-0', locale) ?? String(value);
  }

  private navigateWithState(state: CraftingBrowserQueryState): void {
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

function readQueryState(queryParamMap: ParamMap): CraftingBrowserQueryState {
  return {
    ...defaultQueryState,
    query: queryParamMap.get('query') ?? '',
    professionIds: queryParamMap.getAll('professionIds').map(Number).filter(Number.isFinite),
    minProfit: nullableNumber(queryParamMap.get('minProfit')),
    maxProfit: nullableNumber(queryParamMap.get('maxProfit')),
    minRoiPercent: nullableNumber(queryParamMap.get('minRoiPercent')),
    maxRoiPercent: nullableNumber(queryParamMap.get('maxRoiPercent')),
    minReagentCost: nullableNumber(queryParamMap.get('minReagentCost')),
    maxReagentCost: nullableNumber(queryParamMap.get('maxReagentCost')),
    minOutputPrice: nullableNumber(queryParamMap.get('minOutputPrice')),
    maxOutputPrice: nullableNumber(queryParamMap.get('maxOutputPrice')),
    minOutputPriceChangePercent: nullableNumber(queryParamMap.get('minOutputPriceChangePercent')),
    maxOutputPriceChangePercent: nullableNumber(queryParamMap.get('maxOutputPriceChangePercent')),
    requireCompleteReagentPricing: queryParamMap.get('requireCompleteReagentPricing') === 'true',
    page: clampPage(nullableNumber(queryParamMap.get('page'))),
    pageSize: clampPageSize(nullableNumber(queryParamMap.get('pageSize'))),
    sortBy: readSortBy(queryParamMap.get('sortBy')),
    sortDirection: queryParamMap.get('sortDirection') === 'desc' ? 'desc' : 'asc',
  };
}

const MAX_PAGE_SIZE = 200;

function clampPage(value: number | null): number {
  if (value == null || !Number.isFinite(value)) return 0;
  return Math.max(0, Math.floor(value));
}

function clampPageSize(value: number | null): number {
  if (value == null || !Number.isFinite(value) || value <= 0) {
    return defaultQueryState.pageSize;
  }
  return Math.min(MAX_PAGE_SIZE, Math.floor(value));
}

function nullableNumber(value: string | null): number | null {
  if (value === null || value === '') return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function readSortBy(value: string | null): CraftingSortBy {
  const allowed: readonly CraftingSortBy[] = [
    'itemName',
    'recipeName',
    'professionName',
    'reagentCost',
    'outputPrice',
    'profit',
    'roiPercent',
    'outputPriceChangePercent',
    'profitChangePercent',
    'listingQuantity',
  ];
  return (allowed.find((c) => c === value) ?? 'itemName') as CraftingSortBy;
}

function toQueryParams(
  state: CraftingBrowserQueryState,
): Record<string, string | number | boolean | readonly number[] | null> {
  return {
    query: state.query || null,
    professionIds: state.professionIds.length ? state.professionIds : null,
    minProfit: state.minProfit,
    maxProfit: state.maxProfit,
    minRoiPercent: state.minRoiPercent,
    maxRoiPercent: state.maxRoiPercent,
    minReagentCost: state.minReagentCost,
    maxReagentCost: state.maxReagentCost,
    minOutputPrice: state.minOutputPrice,
    maxOutputPrice: state.maxOutputPrice,
    minOutputPriceChangePercent: state.minOutputPriceChangePercent,
    maxOutputPriceChangePercent: state.maxOutputPriceChangePercent,
    requireCompleteReagentPricing: state.requireCompleteReagentPricing ? true : null,
    page: state.page === 0 ? null : state.page,
    pageSize: state.pageSize === defaultQueryState.pageSize ? null : state.pageSize,
    sortBy: state.sortBy === 'itemName' ? null : state.sortBy,
    sortDirection: state.sortDirection === 'asc' ? null : state.sortDirection,
  };
}

function toFilterSections(
  filters: readonly AuctionMarketFilter[],
  state: CraftingBrowserQueryState,
): readonly FilterSection[] {
  return filters.map((filter) => {
    if (filter.type === AuctionMarketFilter.TypeEnum.Boolean) {
      return {
        id: filter.id,
        label: filter.label,
        type: filter.type,
        options: [
          {
            id: `${filter.id}:true`,
            label: filter.label,
            selected: state.requireCompleteReagentPricing,
          },
        ],
      };
    }
    const selectedIds =
      filter.id === 'professionIds' ? new Set(state.professionIds.map(String)) : new Set();
    return {
      id: filter.id,
      label: filter.label,
      type: filter.type,
      min: filter.min ?? undefined,
      max: filter.max ?? undefined,
      selectedMin: selectedRangeValue(filter.id, 'min', state),
      selectedMax: selectedRangeValue(filter.id, 'max', state),
      options: (filter.options ?? []).map((option) => ({
        id: `${filter.id}:${option.id}`,
        label: option.label,
        selected: selectedIds.has(option.id),
        parentId: option.parentId ?? undefined,
      })),
    };
  });
}

function selectedRangeValue(
  filterId: string,
  bound: 'min' | 'max',
  state: CraftingBrowserQueryState,
): number | undefined {
  if (filterId === 'profit')
    return (bound === 'min' ? state.minProfit : state.maxProfit) ?? undefined;
  if (filterId === 'roiPercent')
    return (bound === 'min' ? state.minRoiPercent : state.maxRoiPercent) ?? undefined;
  if (filterId === 'reagentCost')
    return (bound === 'min' ? state.minReagentCost : state.maxReagentCost) ?? undefined;
  if (filterId === 'outputPrice')
    return (bound === 'min' ? state.minOutputPrice : state.maxOutputPrice) ?? undefined;
  if (filterId === 'outputPriceChangePercent')
    return (
      (bound === 'min' ? state.minOutputPriceChangePercent : state.maxOutputPriceChangePercent) ??
      undefined
    );
  return undefined;
}

function toggleNumber(values: readonly number[], value: number): readonly number[] {
  return values.includes(value)
    ? values.filter((candidate) => candidate !== value)
    : [...values, value];
}

function toCraftingRow(row: CraftingMarketSearchRow): CraftingTableRow {
  const lk = row.listingKey;
  const outPrice = toNum(row.outputPriceCopper);
  const minBuy = outPrice ?? null;
  return {
    rowId: row.rowId,
    recipeId: row.recipeId,
    craftedItemId: row.item?.id ?? 0,
    craftedItemName: row.item?.name ?? '',
    recipeName: row.recipe?.name ?? '',
    professionName: row.professionName ?? '—',
    variantSummary: variantSummary(lk),
    listingKey: {
      bonusKey: lk.bonusKey,
      modifierKey: lk.modifierKey,
      petSpeciesId: lk.petSpeciesId,
    },
    quality: toQuality(row.item?.quality?.type ?? row.item?.quality?.name),
    iconUrl: row.item?.mediaUrl ?? undefined,
    outputPriceCopper: outPrice,
    reagentCostCopper: toNum(row.reagentCostCopper),
    profitCopper: toNum(row.profitCopper),
    roiPercent: row.roiPercent ?? null,
    outputPriceChangePercent: row.outputPriceChangePercent ?? null,
    listingQuantity: row.listingQuantity != null ? Number(row.listingQuantity) : null,
    minBuyoutCopper: minBuy,
  };
}

function variantSummary(lk: {
  bonusKey: string;
  modifierKey: string;
  petSpeciesId: number;
}): string {
  const parts: string[] = [];
  if (lk.bonusKey?.trim()) parts.push(`B:${truncate(lk.bonusKey, 24)}`);
  if (lk.modifierKey?.trim()) parts.push(`M:${truncate(lk.modifierKey, 12)}`);
  if (lk.petSpeciesId) parts.push(`Pet ${lk.petSpeciesId}`);
  return parts.length ? parts.join(' · ') : 'Default';
}

function truncate(s: string, max: number): string {
  return s.length <= max ? s : `${s.slice(0, max - 1)}…`;
}

function toNum(v: unknown): number | null {
  if (v == null) return null;
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  if (typeof v === 'string') {
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

function toQuality(value: string | undefined): import('@ui').ItemQuality {
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
