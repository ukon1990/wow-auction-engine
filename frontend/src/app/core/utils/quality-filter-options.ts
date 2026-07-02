import { AuctionMarketFilter } from '@api/generated';
import { compareQualityType, toQuality } from '@core/utils/quality-order';

type FilterOption = NonNullable<AuctionMarketFilter['options']>[number];

export const resolveFilterOptionQuality = (option: FilterOption): ReturnType<typeof toQuality> =>
  toQuality(option.qualityType ?? option.label);

const qualityTypeKey = (option: FilterOption): string =>
  (option.qualityType ?? option.label).toUpperCase();

export const sortQualityFilterOptions = (
  options: readonly FilterOption[],
): readonly FilterOption[] => {
  const seen = new Set<string>();
  const unique: FilterOption[] = [];

  for (const option of [...options].sort((left, right) =>
    compareQualityType(left.qualityType ?? left.label, right.qualityType ?? right.label),
  )) {
    const key = qualityTypeKey(option);
    if (seen.has(key)) continue;
    seen.add(key);
    unique.push(option);
  }

  return unique;
};

export const mapQualityFilterOptions = (
  filter: AuctionMarketFilter,
  options: readonly FilterOption[],
): readonly FilterOption[] =>
  filter.id === 'qualityIds' ? sortQualityFilterOptions(options) : options;
