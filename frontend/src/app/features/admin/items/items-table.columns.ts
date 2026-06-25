import { ColumnDef, createColumnHelper, flexRenderComponent } from '@tanstack/angular-table';
import { AdminItem } from '@api/generated';
import { ItemActionsCellComponent } from '@features/admin/items/item-actions-cell.component';

type ItemColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
};

export type ItemTableActions = {
  readonly onEdit: (item: AdminItem) => void;
  readonly onRemoveOverride: (item: AdminItem) => void;
};

type ItemActionsColumnMeta = ItemColumnMeta & ItemTableActions;

export const createItemColumns = (actions: ItemTableActions) => {
  const helper = createColumnHelper<AdminItem>();
  return [
    helper.accessor('id', {
      header: 'ID',
      meta: { align: 'left', gridTrack: 'minmax(5rem, 0.6fr)', ...actions } satisfies ItemActionsColumnMeta,
    }),
    helper.accessor('name', {
      header: 'Name',
      cell: (info) => info.getValue() ?? '—',
      meta: { align: 'left', gridTrack: 'minmax(12rem, 2fr)', ...actions } satisfies ItemActionsColumnMeta,
    }),
    helper.accessor('qualityName', {
      header: 'Quality',
      cell: (info) => info.getValue() ?? '—',
      meta: { align: 'left', gridTrack: 'minmax(7rem, 1fr)', ...actions } satisfies ItemActionsColumnMeta,
    }),
    helper.accessor('expansionName', {
      header: 'Expansion',
      cell: (info) => info.getValue() ?? '—',
      meta: { align: 'left', gridTrack: 'minmax(8rem, 1fr)', ...actions } satisfies ItemActionsColumnMeta,
    }),
    helper.accessor('hasOverride', {
      header: 'Override',
      cell: (info) => (info.getValue() ? 'Yes' : 'No'),
      meta: { align: 'left', gridTrack: 'minmax(5rem, 0.5fr)', ...actions } satisfies ItemActionsColumnMeta,
    }),
    helper.display({
      id: 'actions',
      header: 'Actions',
      meta: { align: 'right', gridTrack: 'minmax(10rem, 1fr)', ...actions } satisfies ItemActionsColumnMeta,
      cell: () => flexRenderComponent(ItemActionsCellComponent),
    }),
  ] as ColumnDef<AdminItem, unknown>[];
};
