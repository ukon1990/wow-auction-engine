import { ColumnDef, createColumnHelper } from '@tanstack/angular-table';
import { User } from '@api/generated';

export const createUserColumns = () => {
  const helper = createColumnHelper<User>();
  return [
    helper.accessor('email', {}),
    helper.accessor('email_verified', {}),
    helper.accessor('status', {}),
    helper.accessor('lastModified', {}),
  ] as ColumnDef<User, unknown>[];
};
