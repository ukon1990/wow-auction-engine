import { convertToParamMap } from '@angular/router';
import {
  defaultAdminRecipeFilters,
  readAdminRecipeFilters,
  toAdminRecipeQueryParams,
  toAdminRecipeSearchParams,
} from './recipe-filters';

describe('recipe filters', () => {
  it('maps associated item filters to API parameters', () => {
    expect(
      toAdminRecipeSearchParams({
        ...defaultAdminRecipeFilters(),
        classId: '5',
        subclassId: '1',
        expansionId: '10',
        associatedItemId: 224025,
        associationType: 'reagent',
      }),
    ).toEqual({
      query: undefined,
      hasOverride: undefined,
      itemClassId: 5,
      itemSubclassId: 1,
      expansionId: 10,
      associatedItemId: 224025,
      associationType: 'reagent',
      page: 1,
      pageSize: 25,
    });
  });

  it('round trips URL-backed association filters', () => {
    const filters = readAdminRecipeFilters(
      convertToParamMap({
        classId: '7',
        subclassId: '11',
        expansionId: '9',
        associatedItemId: '210930',
        associationType: 'crafted',
      }),
    );

    expect(filters).toMatchObject({
      classId: '7',
      subclassId: '11',
      expansionId: '9',
      associatedItemId: 210930,
      associationType: 'crafted',
    });
    expect(toAdminRecipeQueryParams(filters)).toMatchObject({
      classId: '7',
      subclassId: '11',
      expansionId: '9',
      associatedItemId: 210930,
      associationType: 'crafted',
    });
  });
});
