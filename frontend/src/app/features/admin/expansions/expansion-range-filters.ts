import { AdminExpansion, AdminExpansionItemRange } from '@api/generated';

export type ExpansionRangeFilterState = {
  readonly expansionId: string;
  readonly source: string;
  readonly enabled: string;
  readonly itemId: string;
};

export const defaultExpansionRangeFilters = (): ExpansionRangeFilterState => ({
  expansionId: '',
  source: '',
  enabled: '',
  itemId: '',
});

export type CreateExpansionRangeDefaults = {
  readonly expansionId: string;
  readonly startItemId: string;
};

export function defaultCreateRangeValues(
  expansions: readonly AdminExpansion[],
  ranges: readonly AdminExpansionItemRange[],
): CreateExpansionRangeDefaults {
  if (expansions.length === 0) {
    return { expansionId: '', startItemId: '' };
  }

  const currentExpansion = expansions.reduce((latest, expansion) =>
    expansion.id > latest.id ? expansion : latest,
  );

  const maxItemId = ranges
    .filter((range) => range.expansion.id === currentExpansion.id)
    .reduce((max, range) => Math.max(max, range.startItemId, range.endItemId), 0);

  return {
    expansionId: String(currentExpansion.id),
    startItemId: String(maxItemId > 0 ? maxItemId : 1),
  };
}

export function filterExpansionRanges(
  ranges: readonly AdminExpansionItemRange[],
  filters: ExpansionRangeFilterState,
): AdminExpansionItemRange[] {
  const itemIdQuery = filters.itemId.trim();
  const parsedItemId = itemIdQuery.length > 0 ? Number.parseInt(itemIdQuery, 10) : Number.NaN;
  const hasItemIdFilter = itemIdQuery.length > 0;

  return ranges.filter((range) => {
    if (filters.expansionId && String(range.expansion.id) !== filters.expansionId) {
      return false;
    }
    if (filters.source && range.source !== filters.source) {
      return false;
    }
    if (filters.enabled === 'true' && !range.enabled) {
      return false;
    }
    if (filters.enabled === 'false' && range.enabled) {
      return false;
    }
    if (hasItemIdFilter) {
      if (!Number.isFinite(parsedItemId)) {
        return false;
      }
      if (parsedItemId < range.startItemId || parsedItemId > range.endItemId) {
        return false;
      }
    }
    return true;
  });
}

export function sortExpansionRanges(
  ranges: readonly AdminExpansionItemRange[],
): AdminExpansionItemRange[] {
  return [...ranges].sort((left, right) => {
    const orderDelta = left.expansion.displayOrder - right.expansion.displayOrder;
    if (orderDelta !== 0) {
      return orderDelta;
    }
    return left.startItemId - right.startItemId;
  });
}
