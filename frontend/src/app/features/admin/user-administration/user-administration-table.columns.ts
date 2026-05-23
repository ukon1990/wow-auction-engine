import { ColumnDef, createColumnHelper } from '@tanstack/angular-table';
import { User } from '@api/generated';

type UserColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
};

export const createUserColumns = () => {
  const helper = createColumnHelper<User>();
  return [
    helper.accessor('email', {
      header: 'Email',
      meta: { align: 'left', gridTrack: 'minmax(16rem, 2fr)' } satisfies UserColumnMeta,
    }),
    helper.accessor('email_verified', {
      header: 'Verified',
      meta: { align: 'left', gridTrack: 'minmax(8rem, 1fr)' } satisfies UserColumnMeta,
    }),
    helper.accessor('status', {
      header: 'Status',
      meta: { align: 'left', gridTrack: 'minmax(8rem, 1fr)' } satisfies UserColumnMeta,
    }),
    helper.accessor('lastModified', {
      header: 'Last modified',
      meta: { align: 'left', gridTrack: 'minmax(12rem, 1fr)' } satisfies UserColumnMeta,
    }),
  ] as ColumnDef<User, unknown>[];
};
