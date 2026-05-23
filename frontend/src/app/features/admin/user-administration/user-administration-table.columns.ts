import { ColumnDef, createColumnHelper, flexRenderComponent } from '@tanstack/angular-table';
import { User } from '@api/generated';
import { DateTimeColumnComponent } from '@ui';

type UserColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
};

export const createUserColumns = () => {
  const helper = createColumnHelper<User>();
  return [
    helper.accessor('email', {
      header: $localize`:@@common:Email`,
      meta: { align: 'left', gridTrack: 'minmax(16rem, 2fr)' } satisfies UserColumnMeta,
    }),
    helper.accessor('email_verified', {
      header: $localize`:@@common:Verified`,
      meta: { align: 'left', gridTrack: 'minmax(8rem, 1fr)' } satisfies UserColumnMeta,
      cell: (context) => (context.getValue() ? $localize`:@@common:Yes` : $localize`:@@common:No`),
    }),
    helper.accessor<'status', string>('status', {
      header: $localize`:@@common:Status`,
      meta: { align: 'left', gridTrack: 'minmax(8rem, 1fr)' } satisfies UserColumnMeta,
      cell: (context) =>
        context.getValue().toLowerCase() === 'confirmed'
          ? $localize`:@@common:Confirmed`
          : $localize`:@@common:Unconfirmed`,
    }),
    helper.accessor('lastModified', {
      header: $localize`:@@common:Last modified`,
      meta: { align: 'left', gridTrack: 'minmax(12rem, 1fr)' } satisfies UserColumnMeta,
      cell: () => flexRenderComponent(DateTimeColumnComponent),
    }),
  ] as ColumnDef<User, unknown>[];
};
