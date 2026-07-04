import { defaultAdminItemFilters, toAdminItemSearchParams } from './item-filters';

describe('admin item filters', () => {
  it('maps zero-based UI pages to one-based API pages', () => {
    const filters = {
      ...defaultAdminItemFilters(),
      page: 2,
      pageSize: 50,
      name: 'potion',
      hasOverride: 'true',
    };

    expect(toAdminItemSearchParams(filters)).toEqual({
      query: 'potion',
      hasOverride: true,
      page: 3,
      pageSize: 50,
    });
  });

  it('uses item ID query before name when both are present', () => {
    const filters = {
      ...defaultAdminItemFilters(),
      itemId: '19019',
      name: 'Thunderfury',
      hasOverride: '',
    };

    expect(toAdminItemSearchParams(filters)).toEqual({
      query: '19019',
      hasOverride: undefined,
      page: 1,
      pageSize: 25,
    });
  });

  it('maps false override filter', () => {
    const filters = {
      ...defaultAdminItemFilters(),
      hasOverride: 'false',
    };

    expect(toAdminItemSearchParams(filters).hasOverride).toBe(false);
  });
});
