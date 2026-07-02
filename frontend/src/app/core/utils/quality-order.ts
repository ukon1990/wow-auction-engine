import { ItemQuality } from '@ui';

const QUALITY_ORDER: readonly ItemQuality[] = [
  'common',
  'uncommon',
  'rare',
  'epic',
  'legendary',
  'artifact',
];

const QUALITY_TYPE_ORDER = [
  'POOR',
  'COMMON',
  'UNCOMMON',
  'RARE',
  'EPIC',
  'LEGENDARY',
  'ARTIFACT',
  'HEIRLOOM',
  'WOW_TOKEN',
] as const;

const QUALITY_TYPE_TO_ITEM_QUALITY: Record<string, ItemQuality> = {
  poor: 'common',
  common: 'common',
  uncommon: 'uncommon',
  rare: 'rare',
  epic: 'epic',
  legendary: 'legendary',
  artifact: 'artifact',
  heirloom: 'common',
  wow_token: 'common',
};

export const toQuality = (value: string | undefined): ItemQuality => {
  const normalized = value?.toLowerCase();
  if (!normalized) return 'common';
  return QUALITY_TYPE_TO_ITEM_QUALITY[normalized] ?? 'common';
};

export const qualityTypeRank = (value: string | undefined): number => {
  const normalized = value?.toUpperCase();
  if (!normalized) return QUALITY_TYPE_ORDER.length;
  const index = QUALITY_TYPE_ORDER.indexOf(normalized as (typeof QUALITY_TYPE_ORDER)[number]);
  return index < 0 ? QUALITY_TYPE_ORDER.length - 1 : index;
};

export const compareQualityType = (left: string | undefined, right: string | undefined): number =>
  qualityTypeRank(left) - qualityTypeRank(right);

export const qualityRank = (quality: ItemQuality): number => {
  const index = QUALITY_ORDER.indexOf(quality);
  return index < 0 ? QUALITY_ORDER.length : index;
};

export const compareQuality = (left: ItemQuality, right: ItemQuality): number =>
  qualityRank(left) - qualityRank(right);
