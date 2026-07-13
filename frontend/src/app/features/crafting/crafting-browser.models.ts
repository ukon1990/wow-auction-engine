import type { ItemQuality, MarketListingKey } from '@ui';
import type { CraftingProfileFit } from '@api/generated';

export interface CraftingTableRow {
  readonly rowId: string;
  readonly recipeId: number;
  readonly craftedItemId: number;
  readonly craftedItemName: string;
  readonly recipeName: string;
  readonly recipeRank?: number | null;
  readonly professionName: string;
  readonly variantSummary: string;
  readonly listingKey: MarketListingKey;
  readonly quality: ItemQuality;
  readonly iconUrl?: string;
  readonly outputPriceCopper: number | null;
  readonly outputP25PriceCopper: number | null;
  readonly outputP75PriceCopper: number | null;
  readonly reagentCostCopper: number | null;
  readonly profitCopper: number | null;
  readonly roiPercent: number | null;
  readonly outputPriceChangePercent: number | null;
  readonly listingQuantity: number | null;
  readonly minBuyoutCopper: number | null;
  readonly profileFit: CraftingProfileFit | null;
}
