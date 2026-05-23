import { ColumnDef, createColumnHelper } from '@tanstack/angular-table';
import { User } from '@api/generated';

export const createUserColumns = () => {
  const helper = createColumnHelper<User>();
  return [helper.accessor('username', {})] as ColumnDef<User, unknown>[];
};
