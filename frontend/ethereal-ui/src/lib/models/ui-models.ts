export type CurrencyKind = 'gold' | 'silver' | 'copper';

export interface CurrencyAmount {
  readonly gold?: number;
  readonly silver?: number;
  readonly copper?: number;
}

export type ItemQuality = 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary';

export interface NavItem {
  readonly id: string;
  readonly label: string;
  readonly icon: string;
  /** When set, the entry renders as a router link instead of a button. */
  readonly routerLink?: string | readonly (string | number)[];
  readonly children?: readonly NavItem[];
}

export interface CharacterSummary {
  readonly name: string;
  readonly realm: string;
  readonly level: number;
  readonly profession: string;
  readonly skill: string;
  readonly avatarUrl?: string;
}

export interface FilterOption {
  readonly id: string;
  readonly label: string;
  readonly selected: boolean;
  readonly quality?: ItemQuality;
}

export interface FilterSection {
  readonly id: string;
  readonly label: string;
  readonly options: readonly FilterOption[];
}

export interface TableColumn {
  readonly id: string;
  readonly label: string;
  readonly align?: 'left' | 'right';
}

export interface MarketItemRow {
  readonly id: string;
  readonly name: string;
  readonly quality: ItemQuality;
  readonly minBuyout: CurrencyAmount;
  readonly marketValue: CurrencyAmount;
  readonly regionalAverage: CurrencyAmount;
  readonly saleRate: number;
  readonly iconUrl?: string;
  readonly selected?: boolean;
}
