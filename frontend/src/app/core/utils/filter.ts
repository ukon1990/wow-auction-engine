import { FilterSection, ItemQuality } from '@ui';
import { AuctionMarketFilter } from '@api/generated';
import { CraftingBrowserQueryState } from '@core/models/crafting-browser.models';
import { MarketBrowserQueryState } from '@core/models/market-browser.models';
import { toQuality } from '@core/utils/quality-order';

export { toQuality } from '@core/utils/quality-order';

export const filterLabel = (filter: AuctionMarketFilter): string => {
  switch (filter.id) {
    case 'price':
      return $localize`:@@market.column.price:Price`;
    case 'quantity':
      return $localize`:@@market.column.quantity:Quantity`;
    case 'qualityIds':
      return $localize`:@@market.column.quality:Quality`;
    case 'itemClassIds':
      return $localize`:@@market.column.class:Class`;
    case 'itemSubclassIds':
      return $localize`:@@market.column.subclass:Subclass`;
    case 'expansionIds':
      return $localize`:@@filters.expansion:Expansion`;
    case 'recipeOnly':
      return $localize`:@@filters.hasRecipe:Has Recipe`;
    default:
      return filter.label;
  }
};

export const filterOptionLabel = (filterId: string, label: string, qualityType?: string | null): string => {
  if (filterId !== 'qualityIds') return label;
  return qualityLabel(toQuality(qualityType ?? label));
};

export const qualityLabel = (quality: ItemQuality): string => {
  switch (quality) {
    case 'common':
      return $localize`:@@quality.common:Common`;
    case 'uncommon':
      return $localize`:@@quality.uncommon:Uncommon`;
    case 'rare':
      return $localize`:@@quality.rare:Rare`;
    case 'epic':
      return $localize`:@@quality.epic:Epic`;
    case 'legendary':
      return $localize`:@@quality.legendary:Legendary`;
  }
};

export const filterType = (filter: AuctionMarketFilter): FilterSection['type'] => {
  if (filter.id === 'itemClassIds' || filter.id === 'itemSubclassIds') return 'select';
  return filter.type;
};

export const filterOptions = (
  filter: AuctionMarketFilter,
  state: MarketBrowserQueryState,
): readonly NonNullable<AuctionMarketFilter['options']>[number][] => {
  const options = filter.options ?? [];
  if (filter.id !== 'itemSubclassIds' || (state.itemClassIds || []).length === 0) return options;
  const selectedClassIds = new Set(state.itemClassIds.map(String));
  return options.filter(
    (option) => option.parentId !== null && selectedClassIds.has(String(option.parentId)),
  );
};

export const filterOptionId = (
  filterId: string,
  option: NonNullable<AuctionMarketFilter['options']>[number],
): string => {
  if (filterId === 'itemSubclassIds' && option.parentId !== null && option.parentId !== undefined) {
    return `${filterId}:${option.parentId}:${option.id}`;
  }
  return `${filterId}:${option.id}`;
};

export const selectedSet = (filterId: string, state: MarketBrowserQueryState): Set<string> => {
  if (filterId === 'qualityIds') return new Set((state.qualityIds || []).map(String));
  if (filterId === 'itemClassIds') return new Set((state.itemClassIds || []).map(String));
  if (filterId === 'itemSubclassIds') return new Set((state.itemSubclassIds || []).map(String));
  if (filterId === 'expansionIds') return new Set((state.expansionIds || []).map(String));
  return new Set();
};

export const selectedRangeValue = (
  filterId: string,
  bound: 'min' | 'max',
  state: MarketBrowserQueryState,
): number | undefined => {
  if (filterId === 'price') return (bound === 'min' ? state.minPrice : state.maxPrice) ?? undefined;
  if (filterId === 'quantity')
    return (bound === 'min' ? state.minQuantity : state.maxQuantity) ?? undefined;
  return undefined;
};

export const nonemptyName = (value: string | null | undefined): string | undefined => {
  const t = value?.trim();
  return t && t.length > 0 ? t : undefined;
};

/** Normalizes OpenAPI int64 (number or occasional string) for copper/qty math. */
export const toOptionalFiniteNumber = (value: unknown): number | undefined => {
  if (value == null) return undefined;
  if (typeof value === 'number') return Number.isFinite(value) ? value : undefined;
  if (typeof value === 'string') {
    const n = Number(value);
    return Number.isFinite(n) ? n : undefined;
  }
  return undefined;
};

// --- Query state mutations (shared with crafting later) ---

export type ParsedFilterOptionId = {
  readonly filterId: string;
  readonly value: number;
  readonly parentId?: number;
};

export const MARKET_RANGE_SECTION_KEYS = {
  price: ['minPrice', 'maxPrice'],
  quantity: ['minQuantity', 'maxQuantity'],
} as const satisfies Record<
  string,
  readonly [keyof MarketBrowserQueryState, keyof MarketBrowserQueryState]
>;

export const MARKET_MULTI_SELECT_KEYS = new Set<string>([
  'qualityIds',
  'itemClassIds',
  'itemSubclassIds',
  'expansionIds',
]);

export function parseFilterOptionId(optionId: string): ParsedFilterOptionId | null {
  const parts = optionId.split(':');
  if (parts.length < 2) return null;

  const filterId = parts[0]!;
  if (parts.length === 2) {
    const value = Number(parts[1]);
    return Number.isFinite(value) ? { filterId, value } : null;
  }

  const parentId = Number(parts[1]);
  const value = Number(parts[2]);
  if (!Number.isFinite(parentId) || !Number.isFinite(value)) return null;
  return { filterId, parentId, value };
}

export function toggleNumberInList(list: readonly number[], id: number): number[] {
  const next = [...list];
  const index = next.indexOf(id);
  if (index >= 0) {
    next.splice(index, 1);
    return next;
  }
  next.push(id);
  return next;
}

export function applyRangeUpdate<State extends object>(
  state: State,
  rangeMap: Readonly<Record<string, readonly [keyof State, keyof State]>>,
  sectionId: string,
  bound: 'min' | 'max',
  value: number | null,
): State {
  const keys = rangeMap[sectionId];
  if (!keys) return state;
  const key = bound === 'min' ? keys[0] : keys[1];
  return { ...state, [key]: value };
}

export function applyMarketFilterToggle(
  state: MarketBrowserQueryState,
  optionId: string,
): MarketBrowserQueryState {
  if (optionId.startsWith('recipeOnly:')) {
    return { ...state, recipeOnly: state.recipeOnly === true ? null : true };
  }

  const parsed = parseFilterOptionId(optionId);
  if (!parsed) return state;

  if (!MARKET_MULTI_SELECT_KEYS.has(parsed.filterId)) return state;

  const key = parsed.filterId as 'qualityIds' | 'itemClassIds' | 'itemSubclassIds' | 'expansionIds';
  const current = [...state[key]];
  const next = toggleNumberInList(current, parsed.value);

  if (key === 'itemClassIds' && next.length < current.length) {
    return { ...state, [key]: next, itemSubclassIds: [] };
  }

  return { ...state, [key]: next };
}

export function applyMarketFilterSelect(
  state: MarketBrowserQueryState,
  sectionId: string,
  optionId: string | null,
): MarketBrowserQueryState {
  if (!optionId) {
    if (sectionId === 'itemClassIds') {
      return { ...state, itemClassIds: [], itemSubclassIds: [] };
    }
    if (sectionId === 'itemSubclassIds') {
      return { ...state, itemSubclassIds: [] };
    }
    if (MARKET_MULTI_SELECT_KEYS.has(sectionId)) {
      return { ...state, [sectionId]: [] } as MarketBrowserQueryState;
    }
    return state;
  }

  const parsed = parseFilterOptionId(optionId);
  if (!parsed || parsed.filterId !== sectionId) return state;

  if (sectionId === 'itemClassIds') {
    return {
      ...state,
      itemClassIds: [parsed.value],
      itemSubclassIds: [],
    };
  }

  if (sectionId === 'itemSubclassIds') {
    const itemSubclassIds = [parsed.value];
    const itemClassIds = parsed.parentId !== undefined ? [parsed.parentId] : state.itemClassIds;
    return { ...state, itemSubclassIds, itemClassIds };
  }

  if (MARKET_MULTI_SELECT_KEYS.has(sectionId)) {
    const key = sectionId as 'qualityIds' | 'itemClassIds' | 'itemSubclassIds' | 'expansionIds';
    return { ...state, [key]: [parsed.value] };
  }

  return state;
}

export function applyMarketRangeFilter(
  state: MarketBrowserQueryState,
  sectionId: string,
  bound: 'min' | 'max',
  value: number | null,
): MarketBrowserQueryState {
  return applyRangeUpdate(state, MARKET_RANGE_SECTION_KEYS, sectionId, bound, value);
}

export const CRAFTING_RANGE_SECTION_KEYS = {
  profit: ['minProfit', 'maxProfit'],
  roiPercent: ['minRoiPercent', 'maxRoiPercent'],
  reagentCost: ['minReagentCost', 'maxReagentCost'],
  outputPrice: ['minOutputPrice', 'maxOutputPrice'],
  outputPriceChangePercent: ['minOutputPriceChangePercent', 'maxOutputPriceChangePercent'],
} as const satisfies Record<
  string,
  readonly [keyof CraftingBrowserQueryState, keyof CraftingBrowserQueryState]
>;

export const CRAFTING_MULTI_SELECT_KEYS = new Set<string>(['professionIds', 'expansionIds', 'qualityIds']);

export const craftingSelectedSet = (
  filterId: string,
  state: CraftingBrowserQueryState,
): Set<string> => {
  if (filterId === 'professionIds') return new Set(state.professionIds.map(String));
  if (filterId === 'expansionIds') return new Set(state.expansionIds.map(String));
  if (filterId === 'qualityIds') return new Set(state.qualityIds.map(String));
  return new Set();
};

export const craftingSelectedRangeValue = (
  filterId: string,
  bound: 'min' | 'max',
  state: CraftingBrowserQueryState,
): number | undefined => {
  if (filterId === 'profit')
    return (bound === 'min' ? state.minProfit : state.maxProfit) ?? undefined;
  if (filterId === 'roiPercent')
    return (bound === 'min' ? state.minRoiPercent : state.maxRoiPercent) ?? undefined;
  if (filterId === 'reagentCost')
    return (bound === 'min' ? state.minReagentCost : state.maxReagentCost) ?? undefined;
  if (filterId === 'outputPrice')
    return (bound === 'min' ? state.minOutputPrice : state.maxOutputPrice) ?? undefined;
  if (filterId === 'outputPriceChangePercent')
    return (
      (bound === 'min' ? state.minOutputPriceChangePercent : state.maxOutputPriceChangePercent) ??
      undefined
    );
  return undefined;
};

export function applyCraftingFilterToggle(
  state: CraftingBrowserQueryState,
  optionId: string,
): CraftingBrowserQueryState {
  if (optionId.startsWith('requireCompleteReagentPricing:')) {
    return {
      ...state,
      requireCompleteReagentPricing: !state.requireCompleteReagentPricing,
    };
  }

  const parsed = parseFilterOptionId(optionId);
  if (!parsed || !CRAFTING_MULTI_SELECT_KEYS.has(parsed.filterId)) return state;

  const key = parsed.filterId as 'professionIds' | 'expansionIds' | 'qualityIds';
  return {
    ...state,
    [key]: toggleNumberInList([...state[key]], parsed.value),
  };
}

export function applyCraftingRangeFilter(
  state: CraftingBrowserQueryState,
  sectionId: string,
  bound: 'min' | 'max',
  value: number | null,
): CraftingBrowserQueryState {
  return applyRangeUpdate(state, CRAFTING_RANGE_SECTION_KEYS, sectionId, bound, value);
}
