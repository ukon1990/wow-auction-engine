import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import {
  buildItemDetailUrl,
  itemDetailQueryParams,
  itemDetailVariantFromOpenParams,
} from './item-detail-url.helpers';

describe('item-detail-url helpers', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });
  it('builds variant query params with optional scope and recipe', () => {
    expect(
      itemDetailQueryParams({
        itemId: 238197,
        bonusKey: 'abc',
        modifierKey: 'def',
        petSpeciesId: -1,
        scope: 'commodity',
        recipeId: 52669,
      }),
    ).toEqual({
      bonusKey: 'abc',
      modifierKey: 'def',
      petSpeciesId: -1,
      scope: 'commodity',
      recipeId: 52669,
    });
  });

  it('omits scope and recipe when unset', () => {
    expect(itemDetailQueryParams({ itemId: 1 })).toEqual({
      bonusKey: '',
      modifierKey: '',
      petSpeciesId: 0,
    });
  });

  it('maps open params to variant', () => {
    expect(
      itemDetailVariantFromOpenParams({
        bonusKey: 'x',
        modifierKey: 'y',
        petSpeciesId: 3,
      }),
    ).toEqual({ bonusKey: 'x', modifierKey: 'y', petSpeciesId: 3 });
  });

  it('builds canonical item page url', () => {
    const router = TestBed.inject(Router);
    const url = buildItemDetailUrl(router, 'eu', 'draenor', {
      itemId: 238197,
      scope: 'commodity',
      recipeId: 52669,
    });
    expect(url).toBe(
      '/eu/draenor/item/238197?bonusKey=&modifierKey=&petSpeciesId=0&scope=commodity&recipeId=52669',
    );
  });
});
