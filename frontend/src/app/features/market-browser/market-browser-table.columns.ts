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
};

const GRID_HEADER =
  'grid w-full gap-4 border-b border-white/10 bg-surface-container-high px-container-padding py-4 ee-label text-outline';

const GRID_ROW =
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
      meta: { align: 'left', gridTrack: 'minmax(14rem, 2fr)' } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketItemCellComponent),
    },
    {
      id: 'itemClass',
      accessorKey: 'itemClassName',
      header: $localize`:@@market.column.class:Class`,
      meta: { align: 'left', gridTrack: 'minmax(5rem, 1fr)' } satisfies MarketColumnMeta,
      cell: (info) => textCell(info.getValue()),
    },
    {
      id: 'itemSubclass',
      accessorKey: 'itemSubclassName',
      header: $localize`:@@market.column.subclass:Subclass`,
      meta: { align: 'left', gridTrack: 'minmax(5rem, 1fr)' } satisfies MarketColumnMeta,
      cell: (info) => textCell(info.getValue()),
    },
    {
      id: 'quality',
      accessorKey: 'quality',
      header: $localize`:@@market.column.quality:Quality`,
      meta: { align: 'left', gridTrack: '7rem' } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketQualityCellComponent),
    },
    {
      id: 'selectedPrice',
      accessorKey: 'listingPriceCopper',
      header: $localize`:@@market.column.price:Price`,
      meta: { align: 'right', gridTrack: 'minmax(6rem, max-content)' } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketMetricCellComponent),
    },
    {
      id: 'selectedQuantity',
      accessorKey: 'selectedQuantity',
      header: $localize`:@@market.column.quantity:Quantity`,
      meta: { align: 'right', gridTrack: 'minmax(4.5rem, max-content)' } satisfies MarketColumnMeta,
      cell: () => flexRenderComponent(MarketMetricCellComponent),
    },
  ];
}
