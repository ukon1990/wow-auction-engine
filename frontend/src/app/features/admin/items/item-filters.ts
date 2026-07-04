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
  readonly sort: string;
};

export type AdminItemSearchParams = {
  readonly query?: string;
  readonly hasOverride?: boolean;
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
  sort: '',
});

export function toAdminItemSearchParams(filters: AdminItemFilterState): AdminItemSearchParams {
  const itemId = filters.itemId.trim();
  const name = filters.name.trim();
  const query = itemId.length > 0 ? itemId : name;

  return {
    query: query.length > 0 ? query : undefined,
    hasOverride: parseBooleanFilter(filters.hasOverride),
    page: filters.page + 1,
    pageSize: filters.pageSize,
  };
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
