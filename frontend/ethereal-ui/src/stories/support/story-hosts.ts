import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import type { ColumnDef } from '@tanstack/angular-table';

import {
  AdminEditableCellComponent,
  CheckboxInputComponent,
  CharacterSummary,
  CurrencyAmount,
  CurrencyAmountComponent,
  FilterPanelComponent,
  FilterSection,
  GlassPanelComponent,
  IconButtonComponent,
  ItemStatCardComponent,
  ItemTooltipCardComponent,
  MarketItemRow,
  NavItem,
  PageFrameComponent,
  PillToggleComponent,
  QualityBadgeComponent,
  SearchInputComponent,
  SelectInputComponent,
  SideNavComponent,
  ChartPanelComponent,
  ChartSeries,
  SymbolIconComponent,
  TableComponent,
  TextInputComponent,
  TooltipCardComponent,
  TopNavComponent,
} from '../../public-api';

export const primaryItems: readonly NavItem[] = [
  { id: 'scrying-pool', label: 'Scrying Pool', icon: 'query_stats' },
  { id: 'market-browser', label: 'Market Browser', icon: 'travel_explore' },
  { id: 'crafting-matrix', label: 'Crafting Matrix', icon: 'schema' },
  { id: 'archive', label: 'Archive', icon: 'inventory_2' },
];

export const professionItems: readonly NavItem[] = [
  { id: 'alchemy', label: 'Alchemy', icon: 'water_medium' },
  { id: 'blacksmithing', label: 'Blacksmithing', icon: 'swords' },
  { id: 'enchanting', label: 'Enchanting', icon: 'magic_button' },
  { id: 'jewelcrafting', label: 'Jewelcrafting', icon: 'diamond' },
  { id: 'inscription', label: 'Inscription', icon: 'auto_stories' },
];

export const character: CharacterSummary = {
  name: 'GoblinKing99',
  realm: 'Illidan-US',
  level: 70,
  profession: 'Blacksmithing',
  skill: 'Skill Level 300/300',
};

export const filters: readonly FilterSection[] = [
  {
    id: 'expansion',
    label: 'Expansion',
    options: [
      { id: 'dragonflight', label: 'Dragonflight', selected: false },
      { id: 'shadowlands', label: 'Shadowlands', selected: true },
    ],
  },
  {
    id: 'quality',
    label: 'Quality',
    options: [
      { id: 'uncommon', label: 'Uncommon', selected: true, quality: 'uncommon' },
      { id: 'rare', label: 'Rare', selected: true, quality: 'rare' },
      { id: 'epic', label: 'Epic', selected: true, quality: 'epic' },
    ],
  },
];

export const rows: readonly MarketItemRow[] = [
  {
    id: 'dracothyst',
    name: 'Dracothyst',
    quality: 'epic',
    minBuyout: { gold: 3450 },
    marketValue: { gold: 3510 },
    regionalAverage: { gold: 3480 },
    saleRate: 0.85,
  },
  {
    id: 'awakened-order',
    name: 'Awakened Order',
    quality: 'rare',
    minBuyout: { gold: 420 },
    marketValue: { gold: 415 },
    regionalAverage: { gold: 418 },
    saleRate: 0.92,
    selected: true,
  },
];

const STORY_MARKET_TABLE_MIN = 'min-w-[56rem]';

const STORY_MARKET_TABLE_HEADER_ROW =
  'grid grid-cols-[minmax(14rem,3fr)_7rem_minmax(7rem,1.5fr)_minmax(7rem,1.5fr)_minmax(7rem,1.5fr)_6rem] gap-4 border-b border-white/10 bg-surface-container-high px-6 py-4 ee-label text-outline';

const STORY_MARKET_TABLE_BODY_ROW =
  'grid w-full grid-cols-[minmax(14rem,3fr)_7rem_minmax(7rem,1.5fr)_minmax(7rem,1.5fr)_minmax(7rem,1.5fr)_6rem] items-center gap-4 px-6 py-3 text-left transition hover:bg-white/5';

function storyCurrencyLabel(amount: CurrencyAmount): string {
  const g = amount.gold ?? 0;
  const s = amount.silver ?? 0;
  const c = amount.copper ?? 0;
  if (g) return `${g}g`;
  if (s) return `${s}s`;
  if (c) return `${c}c`;
  return '—';
}

/** TanStack column defs for Storybook (string cells only; no Wowhead). */
export function createMarketStoryTableColumns(): ColumnDef<MarketItemRow, unknown>[] {
  return [
    {
      id: 'item',
      accessorKey: 'name',
      header: 'Item',
      meta: { align: 'left' },
    },
    {
      id: 'quality',
      accessorKey: 'quality',
      header: 'Quality',
      meta: { align: 'left' },
    },
    {
      id: 'min-buyout',
      header: 'Min Buyout',
      meta: { align: 'right' },
      cell: ({ row }) => storyCurrencyLabel(row.original.minBuyout),
    },
    {
      id: 'market-value',
      header: 'Market Value',
      meta: { align: 'right' },
      cell: ({ row }) => storyCurrencyLabel(row.original.marketValue),
    },
    {
      id: 'regional-average',
      header: 'Regional Avg',
      meta: { align: 'right' },
      cell: ({ row }) => storyCurrencyLabel(row.original.regionalAverage),
    },
    {
      id: 'sale-rate',
      header: 'Sale Rate',
      meta: { align: 'right' },
      cell: ({ row }) => row.original.saleRate.toFixed(2),
    },
  ];
}

function storyMarketRowClass(row: MarketItemRow): string {
  return row.selected
    ? `${STORY_MARKET_TABLE_BODY_ROW} border-l-2 border-primary bg-primary/10`
    : STORY_MARKET_TABLE_BODY_ROW;
}

@Component({
  imports: [CurrencyAmountComponent],
  template: `<ee-currency-amount [amount]="amount" [emphasis]="true" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CurrencyAmountStoryHostComponent {
  readonly amount: CurrencyAmount = { gold: 2450, silver: 42, copper: 80 };
}

@Component({
  imports: [GlassPanelComponent],
  template: `
    <ee-glass-panel ariaLabel="Example glass panel">
      <p class="ee-label text-outline">Glass Panel</p>
      <h2 class="mt-2 font-cinzel text-xl font-bold text-primary">Arcane Market Surface</h2>
      <p class="mt-3 max-w-sm text-sm text-on-surface-variant">
        Reusable frosted container for panels, cards, tables, and modal bodies.
      </p>
    </ee-glass-panel>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GlassPanelStoryHostComponent {}

@Component({
  imports: [IconButtonComponent],
  template: `<ee-icon-button icon="settings" label="Settings" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IconButtonStoryHostComponent {}

@Component({
  imports: [SymbolIconComponent],
  template: `
    <div class="grid grid-cols-4 gap-4">
      @for (icon of icons; track icon) {
        <div class="ee-glass flex flex-col items-center gap-2 rounded-lg p-4 text-primary">
          <ee-symbol-icon class="text-3xl" [name]="icon" />
          <span class="ee-data text-outline">{{ icon }}</span>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SymbolIconGridStoryHostComponent {
  readonly icons = [
    'account_circle',
    'settings',
    'query_stats',
    'search',
    'filter_alt',
    'swords',
    'water_medium',
    'magic_button',
    'diamond',
    'auto_stories',
    'show_chart',
    'inventory_2',
  ] as const;
}

@Component({
  imports: [PillToggleComponent, QualityBadgeComponent, SearchInputComponent],
  template: `
    <div class="flex w-[560px] flex-col gap-6">
      <ee-quality-badge quality="epic" />
      <ee-search-input label="Search market" placeholder="Search items, reagents, or recipes..." />
      <ee-pill-toggle label="Market scope" [options]="scopeOptions" [value]="'realm'" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrimitiveControlsStoryHostComponent {
  readonly scopeOptions = [
    { id: 'realm', label: 'Realm' },
    { id: 'region', label: 'Region' },
  ];
}

@Component({
  imports: [QualityBadgeComponent],
  template: `<ee-quality-badge quality="epic" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QualityBadgeStoryHostComponent {}

@Component({
  imports: [SearchInputComponent],
  template: `
    <div class="w-[520px]">
      <ee-search-input label="Search market" placeholder="Search items, reagents, or recipes..." />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchInputStoryHostComponent {}

@Component({
  imports: [PillToggleComponent],
  template: `<ee-pill-toggle label="Market scope" [options]="options" [value]="'realm'" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PillToggleStoryHostComponent {
  readonly options = [
    { id: 'realm', label: 'Realm' },
    { id: 'region', label: 'Region' },
  ];
}

@Component({
  imports: [FilterPanelComponent],
  template: `<div class="flex h-[560px]"><ee-filter-panel [sections]="sections" /></div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterPanelStoryHostComponent {
  readonly sections = filters;
}

@Component({
  imports: [ItemStatCardComponent],
  template: `
    <ee-item-stat-card
      label="Realm Avg Price"
      icon="query_stats"
      [currency]="currency"
      caption="12% above region"
      tone="primary"
    />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemStatCardStoryHostComponent {
  readonly currency: CurrencyAmount = { gold: 2680 };
}

@Component({
  imports: [ItemTooltipCardComponent],
  template: `
    <div class="w-[380px]">
      <ee-item-tooltip-card
        name="Draconic Vial of Awakening"
        subtitle="Item Level 415 - Consumable"
        [lines]="lines"
        flavor="A potent draught distilled from the waking dreams of ancient wyrms."
        [sellPrice]="sellPrice"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemTooltipCardStoryHostComponent {
  readonly lines = [
    'Binds when picked up',
    'Unique (20)',
    'Use: Increases primary stat by 450 for 1 hour.',
    'Requires Level 70',
  ];
  readonly sellPrice: CurrencyAmount = { gold: 15, silver: 42, copper: 80 };
}

@Component({
  selector: 'story-chart-panel-host',
  imports: [ChartPanelComponent, TooltipCardComponent],
  template: `
    <ng-template #chartTip let-ctx>
      <ee-tooltip-card
        [title]="'Bucket ' + ctx.categoryIndex"
        [subtitle]="'x = ' + ctx.x"
        [rows]="tooltipRows(ctx.valuesBySeriesId)"
        [compact]="true"
      />
    </ng-template>
    <div class="w-full max-w-3xl min-w-0">
      <ee-chart-panel
        title="Market snapshot"
        rangeLabel="Last 30 slots"
        [series]="chartSeries"
        [minPixelsPerCategory]="12"
        [minChartWidthPx]="280"
        [tooltipTemplate]="chartTip"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChartPanelStoryHostComponent {
  protected tooltipRows(valuesBySeriesId: Readonly<Record<string, number | undefined>>) {
    return Object.entries(valuesBySeriesId).map(([label, value]) => ({ label, value }));
  }

  readonly chartSeries: readonly ChartSeries[] = (() => {
    const xs = Array.from({ length: 30 }, (_, i) => i);
    return [
      {
        id: 'price',
        kind: 'line' as const,
        yScaleKey: 'gold',
        color: 'primary-container' as const,
        points: xs.map((x, i) => ({ x, y: 2100 + i * 18 + (i % 5) * 12 })),
      },
      {
        id: 'quantity',
        kind: 'column' as const,
        yScaleKey: 'qty',
        color: 'tertiary-container' as const,
        points: xs.map((x, i) => ({ x, y: 40 + i * 3 + (i % 4) * 8 })),
      },
      {
        id: 'roi',
        kind: 'line' as const,
        yScaleKey: 'roi',
        color: 'secondary' as const,
        points: xs.map((x, i) => ({ x, y: 10 + (i % 6) * 1.1 + (i % 3) * 0.4 })),
      },
    ];
  })();
}

@Component({
  imports: [AdminEditableCellComponent],
  template: `
    <div class="w-32">
      <ee-admin-editable-cell label="Yield override" value="1.25" [highlighted]="true" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminEditableCellStoryHostComponent {}

@Component({
  imports: [
    AdminEditableCellComponent,
    CheckboxInputComponent,
    JsonPipe,
    PillToggleComponent,
    ReactiveFormsModule,
    SearchInputComponent,
    SelectInputComponent,
    TextInputComponent,
  ],
  template: `
    <form class="ee-glass grid w-[720px] gap-5 rounded-lg p-inner-padding" [formGroup]="form">
      <ee-text-input
        label="Item name"
        placeholder="Dracothyst"
        hint="Regular text input bound with formControlName."
        formControlName="itemName"
      />
      <ee-search-input
        label="Search market"
        placeholder="Search items, reagents, or recipes..."
        formControlName="search"
      />
      <ee-select-input
        label="Profession"
        placeholder="Choose profession"
        [options]="professionOptions"
        formControlName="profession"
      />
      <ee-checkbox-input
        label="Only show profitable crafts"
        hint="Boolean checkbox control."
        formControlName="profitableOnly"
      />
      <ee-pill-toggle
        label="Market scope"
        [options]="scopeOptions"
        [value]="'realm'"
        formControlName="scope"
      />
      <div class="w-40">
        <ee-admin-editable-cell
          label="Yield override"
          placeholder="1.00"
          [highlighted]="true"
          formControlName="yieldOverride"
        />
      </div>
      <pre class="rounded border border-white/10 bg-black/50 p-4 ee-data text-outline">{{
        form.getRawValue() | json
      }}</pre>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReactiveFormStoryHostComponent {
  readonly form = new FormGroup({
    itemName: new FormControl('Dracothyst', { nonNullable: true }),
    search: new FormControl('Awakened', { nonNullable: true }),
    profession: new FormControl('alchemy', { nonNullable: true }),
    profitableOnly: new FormControl(true, { nonNullable: true }),
    scope: new FormControl('realm', { nonNullable: true }),
    yieldOverride: new FormControl('1.25', { nonNullable: true }),
  });

  readonly professionOptions = [
    { id: 'alchemy', label: 'Alchemy' },
    { id: 'blacksmithing', label: 'Blacksmithing' },
    { id: 'enchanting', label: 'Enchanting' },
  ];

  readonly scopeOptions = [
    { id: 'realm', label: 'Realm' },
    { id: 'region', label: 'Region' },
  ];
}

@Component({
  imports: [TopNavComponent],
  template: `
    <ee-top-nav
      [items]="items"
      activeId="market-browser"
      [character]="character"
      [mobileDrawerOpen]="mobileNavOpen()"
      (toggleMobileDrawer)="toggleMobileNav()"
    />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopNavigationStoryHostComponent {
  readonly items = primaryItems;
  readonly character = character;
  protected readonly mobileNavOpen = signal(false);

  protected toggleMobileNav(): void {
    this.mobileNavOpen.update((open) => !open);
  }
}

@Component({
  imports: [SideNavComponent],
  template: `
    <div class="flex h-[560px] flex-col bg-background text-on-surface">
      <p class="p-2 ee-label text-outline md:hidden">
        Narrow the viewport (&lt;768px) to test the drawer; use the control below to open it.
      </p>
      <button
        type="button"
        class="mx-2 mb-2 rounded border border-outline/30 px-3 py-2 ee-label text-on-surface md:hidden"
        (click)="mobileNavOpen.set(true)"
      >
        Open navigation drawer
      </button>
      <div class="flex min-h-0 flex-1">
        <ee-side-nav
          [items]="items"
          activeId="blacksmithing"
          [character]="character"
          [primaryNavItems]="primaryItems"
          activePrimaryId="market-browser"
          [mobileOpen]="mobileNavOpen()"
          (mobileOpenChange)="mobileNavOpen.set($event)"
        />
        <div class="ee-glass m-4 hidden min-w-0 flex-1 rounded-lg p-4 md:block">
          Main content (desktop)
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SideNavigationStoryHostComponent {
  readonly items = professionItems;
  readonly primaryItems = primaryItems;
  readonly character = character;
  protected readonly mobileNavOpen = signal(false);
}

@Component({
  imports: [PageFrameComponent, SearchInputComponent],
  template: `
    <ee-page-frame title="Market Browser" eyebrow="Exchange Intelligence">
      <ee-search-input />
      <div class="ee-glass rounded-lg p-inner-padding text-on-surface-variant">
        Routed feature pages compose reusable UI components inside this frame.
      </div>
    </ee-page-frame>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageFrameStoryHostComponent {}

@Component({
  imports: [
    FilterPanelComponent,
    PageFrameComponent,
    SearchInputComponent,
    SideNavComponent,
    TableComponent,
    TopNavComponent,
  ],
  template: `
    <div class="flex h-screen flex-col overflow-hidden bg-background text-on-surface">
      <ee-top-nav
        [items]="primaryItems"
        activeId="market-browser"
        [character]="character"
        [mobileDrawerOpen]="mobileNavOpen()"
        (toggleMobileDrawer)="toggleMobileNav()"
      />
      <div class="flex min-h-0 flex-1">
        <ee-side-nav
          [items]="professionItems"
          activeId="blacksmithing"
          [character]="character"
          [primaryNavItems]="primaryItems"
          activePrimaryId="market-browser"
          [mobileOpen]="mobileNavOpen()"
          (mobileOpenChange)="mobileNavOpen.set($event)"
        />
        <ee-page-frame title="Market Browser" eyebrow="Exchange Intelligence">
          <ee-search-input />
          <div class="flex min-h-0 flex-1 gap-element-gap overflow-hidden">
            <ee-filter-panel [sections]="filters" />
            <ee-table
              [data]="rows"
              [columns]="storyColumns"
              [getRowId]="storyGetRowId"
              sectionAriaLabel="Market items"
              emptyMessage="No market items available."
              [contentMinWidthClass]="storyTableMin"
              [headerRowClass]="storyTableHeader"
              [bodyRowClassFn]="storyRowClassFn"
              [showFooter]="true"
              footerSummary="Showing 1-2 of 1,248 items"
              [showPagination]="true"
            />
          </div>
        </ee-page-frame>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketBrowserStoryHostComponent {
  readonly primaryItems = primaryItems;
  readonly professionItems = professionItems;
  readonly character = character;
  readonly filters = filters;
  readonly rows = rows;
  readonly storyColumns = createMarketStoryTableColumns();
  readonly storyTableMin = STORY_MARKET_TABLE_MIN;
  readonly storyTableHeader = STORY_MARKET_TABLE_HEADER_ROW;
  readonly storyRowClassFn = storyMarketRowClass;
  readonly storyGetRowId = (row: MarketItemRow) => row.id;
  protected readonly mobileNavOpen = signal(false);

  protected toggleMobileNav(): void {
    this.mobileNavOpen.update((open) => !open);
  }
}
