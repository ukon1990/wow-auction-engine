import type { ColumnDef } from '@tanstack/angular-table';
import { flexRenderComponent } from '@tanstack/angular-table';

import { CraftingCurrencyCellComponent } from './crafting-currency-cell.component';
import { CraftingItemCellComponent } from './crafting-item-cell.component';
import { CraftingPercentCellComponent } from './crafting-percent-cell.component';
import type { CraftingTableRow } from './crafting-browser.models';

type ColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
  readonly cardRole: 'primary' | 'metric' | 'detail';
  readonly cardLabel?: string;
  readonly cardPriority: number;
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
      header: $localize`:@@crafting.column.item:Item`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(18rem, 2.5fr)',
        cardRole: 'primary',
        cardPriority: 0,
      } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingItemCellComponent),
    },
    {
      id: 'professionName',
      accessorKey: 'professionName',
      header: $localize`:@@crafting.column.profession:Profession`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(6rem, 1fr)',
        cardRole: 'detail',
        cardLabel: $localize`:@@crafting.column.profession:Profession`,
        cardPriority: 20,
      } satisfies ColumnMeta,
      cell: (info) => textCell(info.getValue()),
    },
    {
      id: 'reagentCost',
      accessorKey: 'reagentCostCopper',
      header: $localize`:@@crafting.column.materialCost:Mat. cost`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(5.5rem, max-content)',
        cardRole: 'metric',
        cardLabel: $localize`:@@crafting.column.materialCost:Mat. cost`,
        cardPriority: 30,
      } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingCurrencyCellComponent),
    },
    {
      id: 'outputPrice',
      accessorKey: 'outputPriceCopper',
      header: $localize`:@@crafting.column.buyout:Buyout`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(5.5rem, max-content)',
        cardRole: 'metric',
        cardLabel: $localize`:@@crafting.column.buyout:Buyout`,
        cardPriority: 10,
      } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingCurrencyCellComponent),
    },
    {
      id: 'profit',
      accessorKey: 'profitCopper',
      header: $localize`:@@crafting.column.profit:Profit`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(5.5rem, max-content)',
        cardRole: 'metric',
        cardLabel: $localize`:@@crafting.column.profit:Profit`,
        cardPriority: 20,
      } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingCurrencyCellComponent),
    },
    {
      id: 'roiPercent',
      accessorKey: 'roiPercent',
      header: $localize`:@@crafting.column.roi:ROI`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(4rem, max-content)',
        cardRole: 'metric',
        cardLabel: $localize`:@@crafting.column.roi:ROI`,
        cardPriority: 40,
      } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingPercentCellComponent),
    },
    {
      id: 'outputPriceChangePercent',
      accessorKey: 'outputPriceChangePercent',
      header: $localize`:@@crafting.column.trend:Trend`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(4rem, max-content)',
        cardRole: 'metric',
        cardLabel: $localize`:@@crafting.column.trend:Trend`,
        cardPriority: 50,
      } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingPercentCellComponent),
    },
    {
      id: 'saleRate',
      accessorKey: 'saleRate',
      enableSorting: false,
      header: $localize`:@@crafting.column.saleRate:Sale rate`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(4.5rem, max-content)',
        cardRole: 'metric',
        cardLabel: $localize`:@@crafting.column.saleRate:Sale rate`,
        cardPriority: 55,
      } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingPercentCellComponent),
    },
    {
      id: 'soldPerDay',
      accessorKey: 'soldPerDay',
      enableSorting: false,
      header: $localize`:@@crafting.column.soldPerDay:Avg sold/day`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(5rem, max-content)',
        cardRole: 'metric',
        cardLabel: $localize`:@@crafting.column.soldPerDay:Avg sold/day`,
        cardPriority: 56,
      } satisfies ColumnMeta,
      cell: () => flexRenderComponent(CraftingPercentCellComponent),
    },
  ];
}
