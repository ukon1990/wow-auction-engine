import { defaultAdminItemFilters, toAdminItemSearchParams } from './item-filters';

describe('admin item filters', () => {
  it('maps zero-based UI pages to one-based API pages', () => {
    const filters = {
      ...defaultAdminItemFilters(),
      page: 2,
      pageSize: 50,
      name: 'potion',
      hasOverride: 'true',
      classId: '7',
      subclassId: '1',
    };

    expect(toAdminItemSearchParams(filters)).toEqual({
      query: 'potion',
      hasOverride: true,
      itemClassId: 7,
      itemSubclassId: 1,
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
      itemClassId: undefined,
      itemSubclassId: undefined,
      page: 1,
      pageSize: 25,
    });
  });

  it('preserves consumable class id zero', () => {
    const filters = {
      ...defaultAdminItemFilters(),
      classId: '0',
      subclassId: '1',
    };

    expect(toAdminItemSearchParams(filters)).toEqual({
      query: undefined,
      hasOverride: undefined,
      itemClassId: 0,
      itemSubclassId: 1,
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
