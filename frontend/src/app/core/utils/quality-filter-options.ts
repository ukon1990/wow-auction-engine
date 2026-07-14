import { ItemQuality } from '@ui';
import { AuctionMarketFilter } from '@api/generated';
import { compareQuality, compareQualityType, toQuality } from '@core/utils/quality-order';

type FilterOption = NonNullable<AuctionMarketFilter['options']>[number];

export const resolveFilterOptionQuality = (option: FilterOption): ReturnType<typeof toQuality> =>
  toQuality(option.qualityType ?? option.label);

const CANONICAL_QUALITY_TYPE: Record<ItemQuality, string> = {
  common: 'COMMON',
  uncommon: 'UNCOMMON',
  rare: 'RARE',
  epic: 'EPIC',
  legendary: 'LEGENDARY',
  artifact: 'ARTIFACT',
};

const isCanonicalQualityType = (option: FilterOption, quality: ItemQuality): boolean =>
  (option.qualityType ?? option.label).toUpperCase() === CANONICAL_QUALITY_TYPE[quality];

const compareQualityFilterOptions = (left: FilterOption, right: FilterOption): number => {
  const leftQuality = resolveFilterOptionQuality(left);
  const rightQuality = resolveFilterOptionQuality(right);
  const byQuality = compareQuality(leftQuality, rightQuality);
  if (byQuality !== 0) return byQuality;

  const leftCanonical = isCanonicalQualityType(left, leftQuality);
  const rightCanonical = isCanonicalQualityType(right, rightQuality);
  if (leftCanonical !== rightCanonical) return leftCanonical ? -1 : 1;

  return compareQualityType(left.qualityType ?? left.label, right.qualityType ?? right.label);
};

export const sortQualityFilterOptions = (
  options: readonly FilterOption[],
): readonly FilterOption[] => {
  const seen = new Set<ItemQuality>();
  const unique: FilterOption[] = [];

  for (const option of [...options].sort(compareQualityFilterOptions)) {
    const quality = resolveFilterOptionQuality(option);
    if (seen.has(quality)) continue;
    seen.add(quality);
    unique.push(option);
  }

  return unique;
};

export const mapQualityFilterOptions = (
  filter: AuctionMarketFilter,
  options: readonly FilterOption[],
): readonly FilterOption[] =>
  filter.id === 'qualityIds' ? sortQualityFilterOptions(options) : options;
