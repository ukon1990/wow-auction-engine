export type CraftingSortBy =
  | 'itemName'
  | 'recipeName'
  | 'professionName'
  | 'reagentCost'
  | 'outputPrice'
  | 'profit'
  | 'roiPercent'
  | 'outputPriceChangePercent'
  | 'profitChangePercent'
  | 'listingQuantity';

export interface CraftingBrowserQueryState {
  readonly query: string;
  readonly professionIds: readonly number[];
  readonly expansionIds: readonly number[];
  readonly minProfit: number | null;
  readonly maxProfit: number | null;
  readonly minRoiPercent: number | null;
  readonly maxRoiPercent: number | null;
  readonly minReagentCost: number | null;
  readonly maxReagentCost: number | null;
  readonly minOutputPrice: number | null;
  readonly maxOutputPrice: number | null;
  readonly minOutputPriceChangePercent: number | null;
  readonly maxOutputPriceChangePercent: number | null;
  readonly requireCompleteReagentPricing: boolean;
  readonly page: number;
  readonly pageSize: number;
  readonly sortBy: CraftingSortBy;
  readonly sortDirection: 'asc' | 'desc';
}
