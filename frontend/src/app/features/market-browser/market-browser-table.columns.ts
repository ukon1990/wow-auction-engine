import type { ColumnDef } from '@tanstack/angular-table';
import { flexRenderComponent } from '@tanstack/angular-table';

import { MarketItemRow } from '@ui';

import { MarketItemCellComponent } from './market-item-cell.component';
import { MarketMetricCellComponent } from './market-metric-cell.component';
import { MarketQualityCellComponent } from './market-quality-cell.component';

/** Per-column CSS grid tracks (inline `grid-template-columns`; commas are valid here). */
type MarketColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
  readonly cardRole: 'primary' | 'metric' | 'detail';
  readonly cardLabel?: string;
  readonly cardPriority: number;
};

const GRID_HEADER =
  'grid w-full gap-4 border-b border-white/10 bg-surface-container-high px-container-padding py-4 ee-label text-outline';

export const GRID_ROW =
  'grid w-full items-center gap-4 px-container-padding py-3 text-left transition hover:bg-white/5 select-text';

const GRID_ROW_SKELETON = 'grid w-full items-center gap-4 px-container-padding py-3 text-left';

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
  cols: readonly ColumnDef<MarketItemRow, unknown>[],
): string {
  return cols.map((col) => (col.meta as MarketColumnMeta).gridTrack).join(' ');
}

function textCell(value: unknown): string {
  if (value === null || value === undefined) return '—';
  const s = String(value).trim();
  return s.length > 0 ? s : '—';
}

export function createMarketBrowserTableColumns(): ColumnDef<MarketItemRow, unknown>[] {
  return [
    {
      id: 'itemName',
      accessorKey: 'name',
      header: $localize`:@@market.column.item:Item`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(14rem, 2fr)',
        cardRole: 'primary',
        cardPriority: 0,
      } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketItemCellComponent),
    },
    {
      id: 'itemClass',
      accessorKey: 'itemClassName',
      header: $localize`:@@market.column.class:Class`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(5rem, 1fr)',
        cardRole: 'detail',
        cardLabel: $localize`:@@market.column.class:Class`,
        cardPriority: 20,
      } satisfies MarketColumnMeta,
      cell: (info) => textCell(info.getValue()),
    },
    {
      id: 'itemSubclass',
      accessorKey: 'itemSubclassName',
      header: $localize`:@@market.column.subclass:Subclass`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(5rem, 1fr)',
        cardRole: 'detail',
        cardLabel: $localize`:@@market.column.subclass:Subclass`,
        cardPriority: 30,
      } satisfies MarketColumnMeta,
      cell: (info) => textCell(info.getValue()),
    },
    {
      id: 'quality',
      accessorKey: 'quality',
      header: $localize`:@@market.column.quality:Quality`,
      meta: {
        align: 'left',
        gridTrack: '7rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@market.column.quality:Quality`,
        cardPriority: 40,
      } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketQualityCellComponent),
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
      enableSorting: false,
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
      enableSorting: false,
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
