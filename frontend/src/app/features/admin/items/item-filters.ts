export type ItemFilterState = {
  readonly itemId: string;
  readonly name: string;
  readonly qualityId: string;
  readonly classId: string;
  readonly subclassId: string;
  readonly expansionId: string;
  readonly hasOverride: string;
  readonly sort: 'id' | 'name' | 'quality' | 'expansion' | 'updatedAt';
};

export const defaultItemFilters = (): ItemFilterState => ({
  itemId: '',
  name: '',
  qualityId: '',
  classId: '',
  subclassId: '',
  expansionId: '',
  hasOverride: '',
  sort: 'id',
});

export type ItemSearchParams = {
  readonly page: number;
  readonly pageSize: number;
  readonly itemId?: number;
  readonly name?: string;
  readonly qualityId?: number;
  readonly classId?: number;
  readonly subclassId?: number;
  readonly expansionId?: number;
  readonly hasOverride?: boolean;
  readonly sort: ItemFilterState['sort'];
};

export const toItemSearchParams = (
  filters: ItemFilterState,
  page: number,
  pageSize: number,
): ItemSearchParams => ({
  page,
  pageSize,
  itemId: parseOptionalInt(filters.itemId),
  name: filters.name.trim() || undefined,
  qualityId: parseOptionalInt(filters.qualityId),
  classId: parseOptionalInt(filters.classId),
  subclassId: parseOptionalInt(filters.subclassId),
  expansionId: parseOptionalInt(filters.expansionId),
  hasOverride:
    filters.hasOverride === ''
      ? undefined
      : filters.hasOverride === 'true',
  sort: filters.sort,
});

function parseOptionalInt(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}
