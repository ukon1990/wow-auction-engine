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

export interface LocaleOption {
  readonly id: string;
  readonly label: string;
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
  readonly parentId?: string;
}

export interface FilterSection {
  readonly id: string;
  readonly label: string;
  readonly type?: 'text' | 'select' | 'multiSelect' | 'boolean' | 'range';
  readonly options: readonly FilterOption[];
  readonly min?: number;
  readonly max?: number;
  readonly selectedMin?: number;
  readonly selectedMax?: number;
}

export interface TableColumn {
  readonly id: string;
  readonly label: string;
  readonly align?: 'left' | 'right';
}

export interface MarketListingKey {
  readonly bonusKey: string;
  readonly modifierKey: string;
  readonly petSpeciesId: number;
}

export interface MarketItemRow {
  readonly id: string;
  readonly name: string;
  readonly preferredScope?: 'realm' | 'commodity';
  /** True when listing price/quantity come from regional commodity only (same as API `isCommodity`). */
  readonly isCommodity?: boolean;
  readonly listingKey?: MarketListingKey;
  readonly itemClassName?: string;
  readonly itemSubclassName?: string;
  readonly quality: ItemQuality;
  /** Raw copper for server-aligned sort; mirrors unified listing price. */
  readonly listingPriceCopper?: number;
  readonly minBuyout: CurrencyAmount;
  readonly marketValue: CurrencyAmount;
  readonly regionalAverage: CurrencyAmount;
  readonly saleRate: number;
  readonly selectedQuantity?: number;
  readonly commodityQuantity?: number;
  readonly iconUrl?: string;
  readonly selected?: boolean;
}
