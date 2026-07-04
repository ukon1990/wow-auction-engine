import { AuctionMarketFilter } from '@api/generated';
import { CraftingBrowserQueryState } from '@core/models/crafting-browser.models';
import {
  craftingSelectedRangeValue,
  craftingSelectedSet,
  filterOptionLabel,
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
    const selectedIds = craftingSelectedSet(filter.id, state);
    return {
      id: filter.id,
      label: filter.label,
      type: filter.type,
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
