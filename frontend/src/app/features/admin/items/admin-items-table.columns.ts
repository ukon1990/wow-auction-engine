import { ColumnDef, createColumnHelper, flexRenderComponent } from '@tanstack/angular-table';
import { AdminItem1 } from '@api/generated';
import { AdminItemActionsCellComponent } from './admin-item-actions-cell.component';
import { AdminItemQualityCellComponent } from './admin-item-quality-cell.component';
import { AdminItemStateCellComponent } from './admin-item-state-cell.component';

type AdminItemColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
  readonly cardRole?: 'primary' | 'metric' | 'detail';
  readonly cardLabel?: string;
  readonly cardPriority?: number;
} & AdminItemTableActions;

export type AdminItemTableActions = {
  readonly onEdit: (item: AdminItem1) => void;
  readonly onCompare: (item: AdminItem1) => void;
  readonly onDeleteOverride: (item: AdminItem1) => void;
};

export const createAdminItemColumns = (actions: AdminItemTableActions) => {
  const helper = createColumnHelper<AdminItem1>();
  return [
    helper.accessor('id', {
      header: $localize`:@@admin.items.table.id:Item ID`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(6rem, 0.65fr)',
        cardRole: 'metric',
        cardLabel: $localize`:@@admin.items.table.id:Item ID`,
        ...actions,
      } satisfies AdminItemColumnMeta,
    }),
    helper.accessor((row) => row.effective.name ?? $localize`:@@admin.items.unnamed:Unnamed item`, {
      id: 'name',
      header: $localize`:@@admin.items.table.name:Name`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(14rem, 2fr)',
        cardRole: 'primary',
        cardPriority: 0,
        ...actions,
      } satisfies AdminItemColumnMeta,
    }),
    helper.display({
      id: 'quality',
      header: $localize`:@@admin.items.table.quality:Quality`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(7rem, 0.8fr)',
        cardRole: 'metric',
        cardLabel: $localize`:@@admin.items.table.quality:Quality`,
        ...actions,
      } satisfies AdminItemColumnMeta,
      cell: () => flexRenderComponent(AdminItemQualityCellComponent),
    }),
    helper.accessor((row) => row.effective.itemClass?.name ?? '—', {
      id: 'class',
      header: $localize`:@@admin.items.table.class:Class`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(9rem, 1fr)',
        cardRole: 'detail',
        ...actions,
      } satisfies AdminItemColumnMeta,
    }),
    helper.accessor((row) => row.effective.expansion?.name ?? '—', {
      id: 'expansion',
      header: $localize`:@@admin.items.table.expansion:Expansion`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(9rem, 1fr)',
        cardRole: 'detail',
        ...actions,
      } satisfies AdminItemColumnMeta,
    }),
    helper.display({
      id: 'state',
      header: $localize`:@@admin.items.table.state:State`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(10rem, 1fr)',
        cardRole: 'detail',
        ...actions,
      } satisfies AdminItemColumnMeta,
      cell: () => flexRenderComponent(AdminItemStateCellComponent),
    }),
    helper.display({
      id: 'actions',
      header: $localize`:@@admin.items.table.actions:Actions`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(9rem, 0.8fr)',
        ...actions,
      } satisfies AdminItemColumnMeta,
      cell: () => flexRenderComponent(AdminItemActionsCellComponent),
    }),
  ] as ColumnDef<AdminItem1, unknown>[];
};
