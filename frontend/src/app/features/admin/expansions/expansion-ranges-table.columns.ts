import { ColumnDef, createColumnHelper, flexRenderComponent } from '@tanstack/angular-table';
import { AdminExpansion, AdminExpansionItemRange } from '@api/generated';
import { ExpansionCatalogActionsCellComponent } from '@features/admin/expansions/expansion-catalog-actions-cell.component';
import { ExpansionRangeActionsCellComponent } from '@features/admin/expansions/expansion-range-actions-cell.component';
import { DateTimeColumnComponent } from '@ui';

type ExpansionColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
};

export type ExpansionCatalogTableActions = {
  readonly onEdit: (expansion: AdminExpansion) => void;
  readonly onDelete: (expansion: AdminExpansion) => void;
};

type ExpansionCatalogColumnMeta = ExpansionColumnMeta & ExpansionCatalogTableActions;

export const createExpansionCatalogColumns = (actions: ExpansionCatalogTableActions) => {
  const helper = createColumnHelper<AdminExpansion>();
  return [
    helper.accessor('name', {
      header: $localize`:@@admin.expansions.catalog.name:Name`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(12rem, 2fr)',
        ...actions,
      } satisfies ExpansionCatalogColumnMeta,
    }),
    helper.accessor('slug', {
      header: $localize`:@@admin.expansions.catalog.slug:Slug`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(10rem, 1.5fr)',
        ...actions,
      } satisfies ExpansionCatalogColumnMeta,
    }),
    helper.accessor('majorVersion', {
      header: $localize`:@@admin.expansions.catalog.majorVersion:Major version`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(6rem, 0.75fr)',
        ...actions,
      } satisfies ExpansionCatalogColumnMeta,
    }),
    helper.accessor('displayOrder', {
      header: $localize`:@@admin.expansions.catalog.displayOrder:Display order`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(6rem, 0.75fr)',
        ...actions,
      } satisfies ExpansionCatalogColumnMeta,
    }),
    helper.display({
      id: 'actions',
      header: 'Actions',
      meta: {
        align: 'right',
        gridTrack: 'minmax(6rem, 0.75fr)',
        ...actions,
      } satisfies ExpansionCatalogColumnMeta,
      cell: () => flexRenderComponent(ExpansionCatalogActionsCellComponent),
    }),
  ] as ColumnDef<AdminExpansion, unknown>[];
};

export type ExpansionRangeTableActions = {
  readonly onEdit: (range: AdminExpansionItemRange) => void;
  readonly onDelete: (range: AdminExpansionItemRange) => void;
};

type ExpansionRangeColumnMeta = ExpansionColumnMeta & ExpansionRangeTableActions;

export const createExpansionRangeColumns = (actions: ExpansionRangeTableActions) => {
  const helper = createColumnHelper<AdminExpansionItemRange>();
  return [
    helper.accessor((row) => row.expansion.name, {
      id: 'expansion',
      header: $localize`:@@admin.expansions.ranges.expansion:Expansion`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(10rem, 1.5fr)',
        ...actions,
      } satisfies ExpansionRangeColumnMeta,
    }),
    helper.accessor('startItemId', {
      header: $localize`:@@admin.expansions.ranges.startItemId:Start item ID`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(7rem, 1fr)',
        ...actions,
      } satisfies ExpansionRangeColumnMeta,
    }),
    helper.accessor('endItemId', {
      header: $localize`:@@admin.expansions.ranges.endItemId:End item ID`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(7rem, 1fr)',
        ...actions,
      } satisfies ExpansionRangeColumnMeta,
    }),
    helper.accessor('source', {
      header: $localize`:@@admin.expansions.ranges.source:Source`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(7rem, 1fr)',
        ...actions,
      } satisfies ExpansionRangeColumnMeta,
    }),
    helper.accessor('enabled', {
      header: 'Enabled',
      meta: {
        align: 'left',
        gridTrack: 'minmax(5rem, 0.75fr)',
        ...actions,
      } satisfies ExpansionRangeColumnMeta,
      cell: (context) =>
        context.getValue() ? $localize`:@@common.yes:Yes` : $localize`:@@common.no:No`,
    }),
    helper.accessor('note', {
      header: $localize`:@@admin.expansions.ranges.note:Note`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(10rem, 1.5fr)',
        ...actions,
      } satisfies ExpansionRangeColumnMeta,
      cell: (context) => context.getValue() ?? '—',
    }),
    helper.accessor('updatedAt', {
      header: $localize`:@@common.lastModified:Last modified`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(10rem, 1fr)',
        ...actions,
      } satisfies ExpansionRangeColumnMeta,
      cell: () => flexRenderComponent(DateTimeColumnComponent),
    }),
    helper.display({
      id: 'actions',
      header: 'Actions',
      meta: {
        align: 'right',
        gridTrack: 'minmax(6rem, 0.75fr)',
        ...actions,
      } satisfies ExpansionRangeColumnMeta,
      cell: () => flexRenderComponent(ExpansionRangeActionsCellComponent),
    }),
  ] as ColumnDef<AdminExpansionItemRange, unknown>[];
};
