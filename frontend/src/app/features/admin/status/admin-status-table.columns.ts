import { ColumnDef, createColumnHelper } from '@tanstack/angular-table';
import { AdminTableSize } from '@api/generated';

type AdminStatusColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
};

export const createTableSizeColumns = () => {
  const helper = createColumnHelper<AdminTableSize>();
  return [
    helper.accessor('name', {
      header: 'Table',
      meta: { align: 'left', gridTrack: 'minmax(14rem, 2fr)' } satisfies AdminStatusColumnMeta,
    }),
    helper.accessor('rows', {
      header: 'Rows',
      meta: { align: 'right', gridTrack: 'minmax(8rem, 1fr)' } satisfies AdminStatusColumnMeta,
      cell: (context) => numberLabel(context.getValue()),
    }),
    helper.accessor('tableSizeInMb', {
      header: 'Table MB',
      meta: { align: 'right', gridTrack: 'minmax(8rem, 1fr)' } satisfies AdminStatusColumnMeta,
    }),
    helper.accessor('indexSizeInMb', {
      header: 'Index MB',
      meta: { align: 'right', gridTrack: 'minmax(8rem, 1fr)' } satisfies AdminStatusColumnMeta,
    }),
    helper.accessor('sizeInMb', {
      header: 'Total MB',
      meta: { align: 'right', gridTrack: 'minmax(8rem, 1fr)' } satisfies AdminStatusColumnMeta,
    }),
    helper.accessor('freeTableSizeInMb', {
      header: 'Free MB',
      meta: { align: 'right', gridTrack: 'minmax(8rem, 1fr)' } satisfies AdminStatusColumnMeta,
    }),
    helper.accessor('allocatedTableSize', {
      header: 'Allocated MB',
      meta: { align: 'right', gridTrack: 'minmax(10rem, 1fr)' } satisfies AdminStatusColumnMeta,
    }),
  ] as ColumnDef<AdminTableSize, unknown>[];
};

function numberLabel(value: number): string {
  return new Intl.NumberFormat().format(value);
}
