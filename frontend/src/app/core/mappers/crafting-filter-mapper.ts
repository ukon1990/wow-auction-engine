import { AuctionMarketFilter } from '@api/generated';
import { CraftingBrowserQueryState } from '@core/models/crafting-browser.models';
import {
  CRAFTING_RANGE_SECTION_KEYS,
  craftingSelectedRangeValue,
  craftingSelectedSet,
  filterLabel,
  filterOptionLabel,
  filterType,
} from '@core/utils/filter';
import {
  mapQualityFilterOptions,
  resolveFilterOptionQuality,
} from '@core/utils/quality-filter-options';
import { FilterSection } from '@ui';

export const toCraftingFilterSections = (
  filters: readonly AuctionMarketFilter[],
  state: CraftingBrowserQueryState,
): readonly FilterSection[] => {
  return filters.map((filter) => {
    const label = filterLabel(filter);
    if (filter.type === AuctionMarketFilter.TypeEnum.Boolean) {
      return {
        id: filter.id,
        label,
        type: filter.type,
        options: [
          {
            id: `${filter.id}:true`,
            label,
            selected: state.requireCompleteReagentPricing,
          },
        ],
      };
    }
    const selectedIds = craftingSelectedSet(filter.id, state);
    const type = Object.hasOwn(CRAFTING_RANGE_SECTION_KEYS, filter.id)
      ? 'range'
      : filterType(filter);
    return {
      id: filter.id,
      label,
      type,
      min: filter.min ?? undefined,
      max: filter.max ?? undefined,
      selectedMin: craftingSelectedRangeValue(filter.id, 'min', state),
      selectedMax: craftingSelectedRangeValue(filter.id, 'max', state),
      options: mapQualityFilterOptions(filter, filter.options ?? []).map((option) => ({
        id: `${filter.id}:${option.id}`,
        label: filterOptionLabel(filter.id, option.label, option.qualityType),
        selected: selectedIds.has(option.id),
        parentId: option.parentId ?? undefined,
        quality: filter.id === 'qualityIds' ? resolveFilterOptionQuality(option) : undefined,
      })),
    };
  });
};
