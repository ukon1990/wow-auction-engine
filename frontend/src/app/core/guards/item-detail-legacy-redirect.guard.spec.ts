import { TestBed } from '@angular/core/testing';
import { convertToParamMap, provideRouter, Router } from '@angular/router';

import {
  legacyAuctionItemRedirectGuard,
  legacyCraftingItemRedirectGuard,
} from './item-detail-legacy-redirect.guard';

describe('item-detail legacy redirect guards', () => {
  it('redirects auctions item route to canonical item page', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const router = TestBed.inject(Router);
    const tree = TestBed.runInInjectionContext(() =>
      legacyAuctionItemRedirectGuard(
        {
          paramMap: convertToParamMap({ itemId: '238197' }),
          queryParams: { scope: 'commodity' },
          parent: {
            paramMap: convertToParamMap({ region: 'eu', realm: 'draenor' }),
            parent: null,
          },
        } as never,
        {} as never,
      ),
    );
    expect(router.serializeUrl(tree as never)).toBe('/eu/draenor/item/238197?scope=commodity');
  });

  it('redirects crafting item route with recipeId query param', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const router = TestBed.inject(Router);
    const tree = TestBed.runInInjectionContext(() =>
      legacyCraftingItemRedirectGuard(
        {
          paramMap: convertToParamMap({ itemId: '238198', recipeId: '52669' }),
          queryParams: { bonusKey: 'x' },
          parent: {
            paramMap: convertToParamMap({ region: 'eu', realm: 'draenor' }),
            parent: null,
          },
        } as never,
        {} as never,
      ),
    );
    expect(router.serializeUrl(tree as never)).toBe(
      '/eu/draenor/item/238198?bonusKey=x&recipeId=52669',
    );
  });
});
