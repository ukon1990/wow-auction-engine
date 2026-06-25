import { AuctionMarketFilter } from '@api/generated';
import { MarketBrowserQueryState } from '@core/models/market-browser.models';
import { FilterSection } from '@ui';
import {
  filterLabel,
  filterOptionId,
  filterOptionLabel,
  filterOptions,
  filterType,
  selectedRangeValue,
  selectedSet,
} from '@core/utils/filter';
import { mapQualityFilterOptions, resolveFilterOptionQuality } from '@core/utils/quality-filter-options';

export const toFilterSections = (
  filters: readonly AuctionMarketFilter[],
  state: MarketBrowserQueryState,
): readonly FilterSection[] => {
  return filters.map((filter) => {
    const selectedIds = selectedSet(filter.id, state);
    const label = filterLabel(filter);
    if (filter.type === AuctionMarketFilter.TypeEnum.Boolean) {
      return {
        id: filter.id,
        label,
        type: filter.type,
        options: [{ id: `${filter.id}:true`, label, selected: state.recipeOnly === true }],
      };
    }
    return {
      id: filter.id,
      label,
      type: filterType(filter),
      min: filter.min ?? undefined,
      max: filter.max ?? undefined,
      selectedMin: selectedRangeValue(filter.id, 'min', state),
      selectedMax: selectedRangeValue(filter.id, 'max', state),
      options: mapQualityFilterOptions(filter, filterOptions(filter, state)).map((option) => ({
        id: filterOptionId(filter.id, option),
        label: filterOptionLabel(filter.id, option.label, option.qualityType),
        selected: selectedIds.has(String(option.id)),
        parentId: option.parentId ?? undefined,
        quality: filter.id === 'qualityIds' ? resolveFilterOptionQuality(option) : undefined,
      })),
    };
  });
};
