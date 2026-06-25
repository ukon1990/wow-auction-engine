import { describe, expect, it } from 'vitest';
import { defaultItemFilters, toItemSearchParams } from '@features/admin/items/item-filters';

describe('item-filters', () => {
  it('maps empty filters to default search params', () => {
    expect(toItemSearchParams(defaultItemFilters(), 0, 50)).toEqual({
      page: 0,
      pageSize: 50,
      sort: 'id',
    });
  });

  it('parses numeric and boolean filters', () => {
    const params = toItemSearchParams(
      {
        ...defaultItemFilters(),
        itemId: '19019',
        name: '  Thunderfury ',
        hasOverride: 'true',
        sort: 'updatedAt',
      },
      2,
      25,
    );

    expect(params).toEqual({
      page: 2,
      pageSize: 25,
      itemId: 19019,
      name: 'Thunderfury',
      hasOverride: true,
      sort: 'updatedAt',
    });
  });
});
