export interface MarketBrowserQueryState {
  readonly query: string;
  readonly qualityIds: readonly number[];
  readonly itemClassIds: readonly number[];
  readonly itemSubclassIds: readonly number[];
  readonly expansionIds: readonly number[];
  readonly recipeOnly: boolean | null;
  readonly minPrice: number | null;
  readonly maxPrice: number | null;
  readonly minQuantity: number | null;
  readonly maxQuantity: number | null;
  readonly minSaleRatePercent: number | null;
  readonly maxSaleRatePercent: number | null;
  readonly minSoldPerDay: number | null;
  readonly maxSoldPerDay: number | null;
  readonly page: number;
  readonly pageSize: number;
  readonly sortBy:
    | 'itemName'
    | 'quality'
    | 'itemClass'
    | 'itemSubclass'
    | 'selectedPrice'
    | 'commodityPrice'
    | 'selectedQuantity'
    | 'commodityQuantity';
  readonly sortDirection: 'asc' | 'desc';
}
