import { ColumnDef, createColumnHelper } from '@tanstack/angular-table';
import { AdminRunningQuery, AdminTableSize } from '@api/generated';

type AdminStatusColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
};

export const createRunningQueryColumns = () => {
  const helper = createColumnHelper<AdminRunningQuery>();
  return [
    helper.accessor('id', {
      header: 'ID',
      meta: { align: 'right', gridTrack: 'minmax(5rem, 0.7fr)' } satisfies AdminStatusColumnMeta,
    }),
    helper.accessor('queryId', {
      header: 'Query ID',
      meta: { align: 'right', gridTrack: 'minmax(7rem, 0.8fr)' } satisfies AdminStatusColumnMeta,
    }),
    helper.accessor('command', {
      header: 'Command',
      meta: { align: 'left', gridTrack: 'minmax(8rem, 0.8fr)' } satisfies AdminStatusColumnMeta,
    }),
    helper.accessor('state', {
      header: 'State',
      meta: { align: 'left', gridTrack: 'minmax(10rem, 1fr)' } satisfies AdminStatusColumnMeta,
      cell: (context) => nullableLabel(context.getValue()),
    }),
    helper.accessor('time', {
      header: 'Time',
      meta: { align: 'right', gridTrack: 'minmax(6rem, 0.7fr)' } satisfies AdminStatusColumnMeta,
    }),
    helper.accessor('timeMs', {
      header: 'Time ms',
      meta: { align: 'right', gridTrack: 'minmax(7rem, 0.8fr)' } satisfies AdminStatusColumnMeta,
      cell: (context) => numberLabel(context.getValue()),
    }),
    helper.accessor('memoryUsed', {
      header: 'Memory MB',
      meta: { align: 'right', gridTrack: 'minmax(8rem, 0.8fr)' } satisfies AdminStatusColumnMeta,
      cell: (context) => nullableNumberLabel(context.getValue()),
    }),
    helper.accessor('examinedRows', {
      header: 'Examined rows',
      meta: { align: 'right', gridTrack: 'minmax(9rem, 1fr)' } satisfies AdminStatusColumnMeta,
      cell: (context) => nullableNumberLabel(context.getValue()),
    }),
    helper.accessor('info', {
      header: 'Info',
      meta: { align: 'left', gridTrack: 'minmax(24rem, 3fr)' } satisfies AdminStatusColumnMeta,
      cell: (context) => nullableLabel(context.getValue()),
    }),
  ] as ColumnDef<AdminRunningQuery, unknown>[];
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

function nullableLabel(value: string | null | undefined): string {
  return value?.trim() || '-';
}

function nullableNumberLabel(value: number | null | undefined): string {
  return value === null || value === undefined ? '-' : numberLabel(value);
}

function numberLabel(value: number): string {
  return new Intl.NumberFormat().format(value);
}
