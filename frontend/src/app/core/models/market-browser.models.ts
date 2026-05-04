import { CharacterSummary, FilterSection, MarketItemRow, NavItem } from '@ui';

export interface MarketBrowserViewModel {
  readonly primaryNavItems: readonly NavItem[];
  readonly activePrimaryNavId: string;
  readonly professionNavItems: readonly NavItem[];
  readonly activeProfessionId: string;
  readonly character: CharacterSummary;
  readonly filterSections: readonly FilterSection[];
  readonly rows: readonly MarketItemRow[];
  readonly paginationSummary: string;
  readonly searchQuery: string;
  readonly page: number;
  readonly totalPages: number;
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
  readonly loading: boolean;
}
