import type { ColumnDef } from '@tanstack/angular-table';
import { flexRenderComponent } from '@tanstack/angular-table';

import { MarketItemRow } from '@ui';

import { MarketItemCellComponent } from './market-item-cell.component';
import { MarketMetricCellComponent } from './market-metric-cell.component';

/** Per-column CSS grid tracks (inline `grid-template-columns`; commas are valid here). */
type MarketColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
  readonly cardRole: 'primary' | 'metric' | 'detail';
  readonly cardLabel?: string;
  readonly cardPriority: number;
};

const GRID_HEADER =
  'grid w-full gap-4 border-b border-white/10 bg-surface-container-high px-inner-padding py-3 ee-label text-outline';

export const GRID_ROW =
  'grid w-full items-center gap-4 px-inner-padding py-2.5 text-left transition hover:bg-white/5 select-text';

const GRID_ROW_SKELETON = 'grid w-full items-center gap-4 px-inner-padding py-2.5 text-left';

const SELECTED_ROW =
  'border-l-2 border-primary bg-primary/10 shadow-[inset_0_0_20px_rgba(236,185,19,0.05)]';

export function marketBrowserHeaderRowClass(): string {
  return GRID_HEADER;
}

export function marketBrowserContentMinWidthClass(): string {
  return 'min-w-0 w-full';
}

export function marketBrowserRowClass(row: MarketItemRow): string {
  return row.selected ? `${GRID_ROW} ${SELECTED_ROW}` : GRID_ROW;
}

export function marketBrowserSkeletonRowClass(): string {
  return GRID_ROW_SKELETON;
}

/** Builds `grid-template-columns` from column defs (pass the same array as `[columns]` on `ee-table`). */
export function marketBrowserRowGridTemplateColumns(
  columns: readonly ColumnDef<MarketItemRow, unknown>[],
): string {
  return columns.map((column) => (column.meta as MarketColumnMeta).gridTrack).join(' ');
}

export function createMarketBrowserTableColumns(): ColumnDef<MarketItemRow, unknown>[] {
  return [
    {
      id: 'itemName',
      accessorKey: 'name',
      header: $localize`:@@market.column.item:Item`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(14rem, 3fr)',
        cardRole: 'primary',
        cardPriority: 0,
      } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketItemCellComponent),
    },
    {
      id: 'selectedPrice',
      accessorKey: 'listingPriceCopper',
      header: $localize`:@@market.column.price:Price`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(6rem, 8rem)',
        cardRole: 'metric',
        cardLabel: $localize`:@@market.column.price:Price`,
        cardPriority: 10,
      } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketMetricCellComponent),
    },
    {
      id: 'selectedQuantity',
      accessorKey: 'selectedQuantity',
      header: $localize`:@@market.column.quantity:Quantity`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(4.5rem, 5.5rem)',
        cardRole: 'metric',
        cardLabel: $localize`:@@market.column.quantity:Quantity`,
        cardPriority: 20,
      } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketMetricCellComponent),
    },
    {
      id: 'saleRate',
      accessorKey: 'saleRate',
      header: $localize`:@@market.column.saleRate:Sale rate`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(4.5rem, 5.5rem)',
        cardRole: 'metric',
        cardLabel: $localize`:@@market.column.saleRate:Sale rate`,
        cardPriority: 25,
      } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketMetricCellComponent),
    },
    {
      id: 'soldPerDay',
      accessorKey: 'soldPerDay',
      header: $localize`:@@market.column.soldPerDay:Avg sold/day`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(5rem, 6rem)',
        cardRole: 'metric',
        cardLabel: $localize`:@@market.column.soldPerDay:Avg sold/day`,
        cardPriority: 26,
      } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketMetricCellComponent),
    },
  ];
}
