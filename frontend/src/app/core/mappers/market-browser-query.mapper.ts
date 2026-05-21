import { ParamMap, Params } from '@angular/router';

import { MarketBrowserQueryState } from '@core/models/market-browser.models';

const SORT_BY_VALUES = [
  'itemName',
  'quality',
  'itemClass',
  'itemSubclass',
  'selectedPrice',
  'commodityPrice',
  'selectedQuantity',
  'commodityQuantity',
] as const satisfies readonly MarketBrowserQueryState['sortBy'][];

const MAX_PAGE_SIZE = 200;

export const defaultMarketBrowserQueryState: MarketBrowserQueryState = {
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

export function readMarketBrowserQueryState(queryParamMap: ParamMap): MarketBrowserQueryState {
  const recipeOnlyParam = queryParamMap.get('recipeOnly');
  return {
    ...defaultMarketBrowserQueryState,
    query: queryParamMap.get('query') ?? '',
    qualityIds: queryParamMap.getAll('qualityIds').map(Number).filter(Number.isFinite),
    itemClassIds: queryParamMap.getAll('itemClassIds').map(Number).filter(Number.isFinite),
    itemSubclassIds: queryParamMap.getAll('itemSubclassIds').map(Number).filter(Number.isFinite),
    recipeOnly: recipeOnlyParam === 'true' ? true : recipeOnlyParam === 'false' ? false : null,
    minPrice: nullableNumber(queryParamMap.get('minPrice')),
    maxPrice: nullableNumber(queryParamMap.get('maxPrice')),
    minQuantity: nullableNumber(queryParamMap.get('minQuantity')),
    maxQuantity: nullableNumber(queryParamMap.get('maxQuantity')),
    page: clampPage(nullableNumber(queryParamMap.get('page'))),
    pageSize: clampPageSize(nullableNumber(queryParamMap.get('pageSize'))),
    sortBy: readSortBy(queryParamMap.get('sortBy')),
    sortDirection: queryParamMap.get('sortDirection') === 'desc' ? 'desc' : 'asc',
  };
}

export function toMarketBrowserQueryParams(state: MarketBrowserQueryState): Params {
  return {
    query: state.query || null,
    qualityIds: state.qualityIds.length ? [...state.qualityIds] : null,
    itemClassIds: state.itemClassIds.length ? [...state.itemClassIds] : null,
    itemSubclassIds: state.itemSubclassIds.length ? [...state.itemSubclassIds] : null,
    recipeOnly: state.recipeOnly === true ? true : state.recipeOnly === false ? false : null,
    minPrice: state.minPrice,
    maxPrice: state.maxPrice,
    minQuantity: state.minQuantity,
    maxQuantity: state.maxQuantity,
    page: state.page === defaultMarketBrowserQueryState.page ? null : state.page,
    pageSize: state.pageSize === defaultMarketBrowserQueryState.pageSize ? null : state.pageSize,
    sortBy: state.sortBy === defaultMarketBrowserQueryState.sortBy ? null : state.sortBy,
    sortDirection:
      state.sortDirection === defaultMarketBrowserQueryState.sortDirection
        ? null
        : state.sortDirection,
  };
}

function readSortBy(value: string | null): MarketBrowserQueryState['sortBy'] {
  return SORT_BY_VALUES.find((column) => column === value) ?? defaultMarketBrowserQueryState.sortBy;
}

function clampPage(value: number | null): number {
  if (value == null || !Number.isFinite(value)) return defaultMarketBrowserQueryState.page;
  return Math.max(1, Math.floor(value));
}

function clampPageSize(value: number | null): number {
  if (value == null || !Number.isFinite(value) || value <= 0) {
    return defaultMarketBrowserQueryState.pageSize;
  }
  return Math.min(MAX_PAGE_SIZE, Math.floor(value));
}

function nullableNumber(value: string | null): number | null {
  if (value === null || value === '') return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}
