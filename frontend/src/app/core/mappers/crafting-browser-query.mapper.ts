import { ParamMap, Params } from '@angular/router';

import { CraftingBrowserQueryState, CraftingSortBy } from '@core/models/crafting-browser.models';

export const CRAFTING_SORT_BY_VALUES = [
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
  'saleRate',
  'soldPerDay',
] as const satisfies readonly CraftingSortBy[];

const MAX_PAGE_SIZE = 200;

export const defaultCraftingBrowserQueryState: CraftingBrowserQueryState = {
  query: '',
  professionIds: [],
  expansionIds: [],
  qualityIds: [],
  minProfit: null,
  maxProfit: null,
  minRoiPercent: null,
  maxRoiPercent: null,
  minSaleRatePercent: null,
  maxSaleRatePercent: null,
  minSoldPerDay: null,
  maxSoldPerDay: null,
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

export function readCraftingBrowserQueryState(queryParamMap: ParamMap): CraftingBrowserQueryState {
  return {
    ...defaultCraftingBrowserQueryState,
    query: queryParamMap.get('query') ?? '',
    professionIds: queryParamMap.getAll('professionIds').map(Number).filter(Number.isFinite),
    expansionIds: queryParamMap.getAll('expansionIds').map(Number).filter(Number.isFinite),
    qualityIds: queryParamMap.getAll('qualityIds').map(Number).filter(Number.isFinite),
    minProfit: nullableNumber(queryParamMap.get('minProfit')),
    maxProfit: nullableNumber(queryParamMap.get('maxProfit')),
    minRoiPercent: nullableNumber(queryParamMap.get('minRoiPercent')),
    maxRoiPercent: nullableNumber(queryParamMap.get('maxRoiPercent')),
    minSaleRatePercent: nullableNumber(queryParamMap.get('minSaleRatePercent')),
    maxSaleRatePercent: nullableNumber(queryParamMap.get('maxSaleRatePercent')),
    minSoldPerDay: nullableNumber(queryParamMap.get('minSoldPerDay')),
    maxSoldPerDay: nullableNumber(queryParamMap.get('maxSoldPerDay')),
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

export function toCraftingBrowserQueryParams(state: CraftingBrowserQueryState): Params {
  return {
    query: state.query || null,
    professionIds: state.professionIds.length ? [...state.professionIds] : null,
    expansionIds: state.expansionIds.length ? [...state.expansionIds] : null,
    qualityIds: state.qualityIds.length ? [...state.qualityIds] : null,
    minProfit: state.minProfit,
    maxProfit: state.maxProfit,
    minRoiPercent: state.minRoiPercent,
    maxRoiPercent: state.maxRoiPercent,
    minSaleRatePercent: state.minSaleRatePercent,
    maxSaleRatePercent: state.maxSaleRatePercent,
    minSoldPerDay: state.minSoldPerDay,
    maxSoldPerDay: state.maxSoldPerDay,
    minReagentCost: state.minReagentCost,
    maxReagentCost: state.maxReagentCost,
    minOutputPrice: state.minOutputPrice,
    maxOutputPrice: state.maxOutputPrice,
    minOutputPriceChangePercent: state.minOutputPriceChangePercent,
    maxOutputPriceChangePercent: state.maxOutputPriceChangePercent,
    requireCompleteReagentPricing: state.requireCompleteReagentPricing ? true : null,
    page: state.page === defaultCraftingBrowserQueryState.page ? null : state.page,
    pageSize: state.pageSize === defaultCraftingBrowserQueryState.pageSize ? null : state.pageSize,
    sortBy: state.sortBy === defaultCraftingBrowserQueryState.sortBy ? null : state.sortBy,
    sortDirection:
      state.sortDirection === defaultCraftingBrowserQueryState.sortDirection
        ? null
        : state.sortDirection,
  };
}

export function readSortBy(value: string | null): CraftingSortBy {
  return (
    CRAFTING_SORT_BY_VALUES.find((column) => column === value) ??
    defaultCraftingBrowserQueryState.sortBy
  );
}

function clampPage(value: number | null): number {
  if (value == null || !Number.isFinite(value)) return defaultCraftingBrowserQueryState.page;
  return Math.max(0, Math.floor(value));
}

function clampPageSize(value: number | null): number {
  if (value == null || !Number.isFinite(value) || value <= 0) {
    return defaultCraftingBrowserQueryState.pageSize;
  }
  return Math.min(MAX_PAGE_SIZE, Math.floor(value));
}

function nullableNumber(value: string | null): number | null {
  if (value === null || value === '') return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}
