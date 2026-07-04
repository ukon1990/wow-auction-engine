import { ParamMap, Params } from '@angular/router';

const DEFAULT_PAGE = 0;
const DEFAULT_PAGE_SIZE = 25;
const MAX_PAGE_SIZE = 100;

export type AdminItemFilterState = {
  readonly page: number;
  readonly pageSize: number;
  readonly itemId: string;
  readonly name: string;
  readonly qualityId: string;
  readonly classId: string;
  readonly subclassId: string;
  readonly expansionId: string;
  readonly hasOverride: string;
  readonly hasRecipe: string;
  readonly sort: string;
};

export type AdminItemSearchParams = {
  readonly query?: string;
  readonly hasOverride?: boolean;
  readonly itemClassId?: number;
  readonly itemSubclassId?: number;
  readonly expansionId?: number;
  readonly hasRecipe?: boolean;
  readonly page: number;
  readonly pageSize: number;
};

export const defaultAdminItemFilters = (): AdminItemFilterState => ({
  page: 0,
  pageSize: 25,
  itemId: '',
  name: '',
  qualityId: '',
  classId: '',
  subclassId: '',
  expansionId: '',
  hasOverride: '',
  hasRecipe: '',
  sort: '',
});

export function toAdminItemSearchParams(filters: AdminItemFilterState): AdminItemSearchParams {
  const itemId = filters.itemId.trim();
  const name = filters.name.trim();
  const query = itemId.length > 0 ? itemId : name;

  return {
    query: query.length > 0 ? query : undefined,
    hasOverride: parseBooleanFilter(filters.hasOverride),
    itemClassId: parseOptionalInt(filters.classId),
    itemSubclassId: parseOptionalInt(filters.subclassId),
    expansionId: parseOptionalInt(filters.expansionId),
    hasRecipe: parseBooleanFilter(filters.hasRecipe),
    page: filters.page + 1,
    pageSize: filters.pageSize,
  };
}

function parseOptionalInt(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number.parseInt(trimmed, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function parseBooleanFilter(value: string): boolean | undefined {
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  return undefined;
}

export function readAdminItemFilters(queryParamMap: ParamMap): AdminItemFilterState {
  const hasOverride = queryParamMap.get('hasOverride');
  const hasRecipe = queryParamMap.get('hasRecipe');
  return {
    ...defaultAdminItemFilters(),
    page: clampPage(nullableNumber(queryParamMap.get('page'))),
    pageSize: clampPageSize(nullableNumber(queryParamMap.get('pageSize'))),
    itemId: queryParamMap.get('itemId') ?? '',
    name: queryParamMap.get('name') ?? '',
    qualityId: queryParamMap.get('qualityId') ?? '',
    classId: readOptionalQueryString(queryParamMap.get('classId')),
    subclassId: readOptionalQueryString(queryParamMap.get('subclassId')),
    expansionId: readOptionalQueryString(queryParamMap.get('expansionId')),
    hasOverride: hasOverride === 'true' || hasOverride === 'false' ? hasOverride : '',
    hasRecipe: hasRecipe === 'true' || hasRecipe === 'false' ? hasRecipe : '',
    sort: queryParamMap.get('sort') ?? '',
  };
}

export function toAdminItemQueryParams(filters: AdminItemFilterState): Params {
  return {
    name: trimmedOrNull(filters.name),
    itemId: trimmedOrNull(filters.itemId),
    qualityId: trimmedOrNull(filters.qualityId),
    classId: filters.classId !== '' ? filters.classId : null,
    subclassId: filters.subclassId !== '' ? filters.subclassId : null,
    expansionId: filters.expansionId !== '' ? filters.expansionId : null,
    hasOverride:
      filters.hasOverride === 'true' || filters.hasOverride === 'false'
        ? filters.hasOverride
        : null,
    hasRecipe:
      filters.hasRecipe === 'true' || filters.hasRecipe === 'false' ? filters.hasRecipe : null,
    sort: trimmedOrNull(filters.sort),
    page: filters.page === DEFAULT_PAGE ? null : filters.page,
    pageSize: filters.pageSize === DEFAULT_PAGE_SIZE ? null : filters.pageSize,
  };
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
