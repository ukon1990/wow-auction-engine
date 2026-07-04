import { convertToParamMap } from '@angular/router';
import {
  defaultAdminItemFilters,
  readAdminItemFilters,
  toAdminItemQueryParams,
  toAdminItemSearchParams,
} from './item-filters';

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

  it('reads filter state from query params', () => {
    expect(
      readAdminItemFilters(
        convertToParamMap({
          name: 'potion',
          classId: '0',
          subclassId: '1',
          hasOverride: 'true',
          page: '2',
          pageSize: '50',
        }),
      ),
    ).toEqual({
      ...defaultAdminItemFilters(),
      name: 'potion',
      classId: '0',
      subclassId: '1',
      hasOverride: 'true',
      page: 2,
      pageSize: 50,
    });
  });

  it('writes non-default filters to query params', () => {
    expect(
      toAdminItemQueryParams({
        ...defaultAdminItemFilters(),
        name: 'potion',
        classId: '0',
        subclassId: '1',
        hasOverride: 'true',
        page: 2,
        pageSize: 50,
      }),
    ).toEqual({
      name: 'potion',
      itemId: null,
      qualityId: null,
      classId: '0',
      subclassId: '1',
      expansionId: null,
      hasOverride: 'true',
      hasRecipe: null,
      sort: null,
      page: 2,
      pageSize: 50,
    });
  });

  it('omits default query params', () => {
    expect(toAdminItemQueryParams(defaultAdminItemFilters())).toEqual({
      name: null,
      itemId: null,
      qualityId: null,
      classId: null,
      subclassId: null,
      expansionId: null,
      hasOverride: null,
      hasRecipe: null,
      sort: null,
      page: null,
      pageSize: null,
    });
  });

  it('maps recipe and expansion filters to search params', () => {
    const filters = {
      ...defaultAdminItemFilters(),
      expansionId: '3',
      hasRecipe: 'false',
    };

    expect(toAdminItemSearchParams(filters)).toEqual({
      query: undefined,
      hasOverride: undefined,
      itemClassId: undefined,
      itemSubclassId: undefined,
      expansionId: 3,
      hasRecipe: false,
      page: 1,
      pageSize: 25,
    });
  });
});
