import type { ColumnDef } from '@tanstack/angular-table';
import { flexRenderComponent } from '@tanstack/angular-table';

import { CraftingCurrencyCellComponent } from './crafting-currency-cell.component';
import { CraftingItemCellComponent } from './crafting-item-cell.component';
import { CraftingPercentCellComponent } from './crafting-percent-cell.component';
import type { CraftingTableRow } from './crafting-browser.models';

type ColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
};

const GRID_HEADER =
  'grid w-full gap-4 border-b border-white/10 bg-surface-container-high px-container-padding py-4 ee-label text-outline';

const GRID_ROW =
  'grid w-full items-center gap-4 px-container-padding py-3 text-left transition hover:bg-white/5 select-text';

const GRID_ROW_SKELETON = 'grid w-full items-center gap-4 px-container-padding py-3 text-left';

export function craftingBrowserHeaderRowClass(): string {
  return GRID_HEADER;
}

export function craftingBrowserRowClass(): string {
  return GRID_ROW;
}

export function craftingBrowserSkeletonRowClass(): string {
  return GRID_ROW_SKELETON;
}

export function craftingBrowserRowGridTemplateColumns(
  cols: readonly ColumnDef<CraftingTableRow, unknown>[],
): string {
  return cols.map((col) => (col.meta as ColumnMeta).gridTrack).join(' ');
}

function textCell(value: unknown): string {
  if (value === null || value === undefined) return '—';
  const s = String(value).trim();
  return s.length > 0 ? s : '—';
}

export function createCraftingBrowserTableColumns(): ColumnDef<CraftingTableRow, unknown>[] {
  return [
    {
      id: 'itemName',
      accessorKey: 'craftedItemName',
      header: $localize`:@@crafting.column.output:Output`,
      meta: { align: 'left', gridTrack: 'minmax(14rem, 2fr)' } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingItemCellComponent),
    },
    {
      id: 'recipeName',
      accessorKey: 'recipeName',
      header: $localize`:@@crafting.column.recipe:Recipe`,
      meta: { align: 'left', gridTrack: 'minmax(8rem, 1.2fr)' } satisfies ColumnMeta,
      cell: (info) => textCell(info.getValue()),
    },
    {
      id: 'professionName',
      accessorKey: 'professionName',
      header: $localize`:@@crafting.column.profession:Profession`,
      meta: { align: 'left', gridTrack: 'minmax(6rem, 1fr)' } satisfies ColumnMeta,
      cell: (info) => textCell(info.getValue()),
    },
    {
      id: 'reagentCost',
      accessorKey: 'reagentCostCopper',
      header: $localize`:@@crafting.column.materialCost:Mat. cost`,
      meta: { align: 'right', gridTrack: 'minmax(5.5rem, max-content)' } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingCurrencyCellComponent),
    },
    {
      id: 'outputPrice',
      accessorKey: 'outputPriceCopper',
      header: $localize`:@@crafting.column.buyout:Buyout`,
      meta: { align: 'right', gridTrack: 'minmax(5.5rem, max-content)' } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingCurrencyCellComponent),
    },
    {
      id: 'profit',
      accessorKey: 'profitCopper',
      header: $localize`:@@crafting.column.profit:Profit`,
      meta: { align: 'right', gridTrack: 'minmax(5.5rem, max-content)' } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingCurrencyCellComponent),
    },
    {
      id: 'roiPercent',
      accessorKey: 'roiPercent',
      header: $localize`:@@crafting.column.roi:ROI`,
      meta: { align: 'right', gridTrack: 'minmax(4rem, max-content)' } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingPercentCellComponent),
    },
    {
      id: 'outputPriceChangePercent',
      accessorKey: 'outputPriceChangePercent',
      header: $localize`:@@crafting.column.trend:Trend`,
      meta: { align: 'right', gridTrack: 'minmax(4rem, max-content)' } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingPercentCellComponent),
    },
  ];
}
