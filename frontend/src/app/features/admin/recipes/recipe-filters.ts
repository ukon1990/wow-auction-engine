import { ParamMap, Params } from '@angular/router';

const DEFAULT_PAGE = 0;
const DEFAULT_PAGE_SIZE = 25;
const MAX_PAGE_SIZE = 100;

export type AdminRecipeFilterState = {
  readonly page: number;
  readonly pageSize: number;
  readonly recipeId: string;
  readonly name: string;
  readonly professionId: string;
  readonly craftedItemId: string;
  readonly hasOverride: string;
};

export type AdminRecipeSearchParams = {
  readonly query?: string;
  readonly professionId?: number;
  readonly craftedItemId?: number;
  readonly hasOverride?: boolean;
  readonly page: number;
  readonly pageSize: number;
};

export const defaultAdminRecipeFilters = (): AdminRecipeFilterState => ({
  page: DEFAULT_PAGE,
  pageSize: DEFAULT_PAGE_SIZE,
  recipeId: '',
  name: '',
  professionId: '',
  craftedItemId: '',
  hasOverride: '',
});

export function toAdminRecipeSearchParams(
  filters: AdminRecipeFilterState,
): AdminRecipeSearchParams {
  const recipeId = filters.recipeId.trim();
  const name = filters.name.trim();
  const query = recipeId.length > 0 ? recipeId : name;

  return {
    query: query.length > 0 ? query : undefined,
    professionId: parseOptionalInt(filters.professionId),
    craftedItemId: parseOptionalInt(filters.craftedItemId),
    hasOverride: parseBooleanFilter(filters.hasOverride),
    page: filters.page + 1,
    pageSize: filters.pageSize,
  };
}

export function readAdminRecipeFilters(queryParamMap: ParamMap): AdminRecipeFilterState {
  const hasOverride = queryParamMap.get('hasOverride');
  return {
    ...defaultAdminRecipeFilters(),
    page: clampPage(nullableNumber(queryParamMap.get('page'))),
    pageSize: clampPageSize(nullableNumber(queryParamMap.get('pageSize'))),
    recipeId: queryParamMap.get('recipeId') ?? '',
    name: queryParamMap.get('name') ?? '',
    professionId: readOptionalQueryString(queryParamMap.get('professionId')),
    craftedItemId: queryParamMap.get('craftedItemId') ?? '',
    hasOverride: hasOverride === 'true' || hasOverride === 'false' ? hasOverride : '',
  };
}

export function toAdminRecipeQueryParams(filters: AdminRecipeFilterState): Params {
  return {
    recipeId: trimmedOrNull(filters.recipeId),
    name: trimmedOrNull(filters.name),
    professionId: filters.professionId !== '' ? filters.professionId : null,
    craftedItemId: trimmedOrNull(filters.craftedItemId),
    hasOverride:
      filters.hasOverride === 'true' || filters.hasOverride === 'false'
        ? filters.hasOverride
        : null,
    page: filters.page === DEFAULT_PAGE ? null : filters.page,
    pageSize: filters.pageSize === DEFAULT_PAGE_SIZE ? null : filters.pageSize,
  };
}

function parseOptionalInt(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number.parseInt(trimmed, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function parseBooleanFilter(value: string): boolean | undefined {
  if (value === 'true') return true;
  if (value === 'false') return false;
  return undefined;
}

function readOptionalQueryString(value: string | null): string {
  if (value === null || value.trim() === '') return '';
  return value;
}

function trimmedOrNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function nullableNumber(value: string | null): number | null {
  if (value === null || value.trim() === '') return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function clampPage(value: number | null): number {
  if (value === null || !Number.isFinite(value)) return DEFAULT_PAGE;
  return Math.max(DEFAULT_PAGE, Math.floor(value));
}

function clampPageSize(value: number | null): number {
  if (value === null || !Number.isFinite(value)) return DEFAULT_PAGE_SIZE;
  return Math.min(MAX_PAGE_SIZE, Math.max(1, Math.floor(value)));
}
