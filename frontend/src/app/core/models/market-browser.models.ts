import { CharacterSummary, FilterSection, MarketItemRow, NavItem, TableColumn } from '@ui';

export interface MarketBrowserViewModel {
  readonly primaryNavItems: readonly NavItem[];
  readonly activePrimaryNavId: string;
  readonly professionNavItems: readonly NavItem[];
  readonly activeProfessionId: string;
  readonly character: CharacterSummary;
  readonly filterSections: readonly FilterSection[];
  readonly tableColumns: readonly TableColumn[];
  readonly rows: readonly MarketItemRow[];
  readonly paginationSummary: string;
}
