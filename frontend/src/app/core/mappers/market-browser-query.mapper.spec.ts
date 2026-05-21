import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';

import {
  defaultMarketBrowserQueryState,
  readMarketBrowserQueryState,
  toMarketBrowserQueryParams,
} from './market-browser-query.mapper';

describe('market-browser-query.mapper', () => {
  it('returns defaults for an empty query string', () => {
    expect(readMarketBrowserQueryState(convertToParamMap({}))).toEqual(
      defaultMarketBrowserQueryState,
    );
  });

  it('reads sort params from the URL', () => {
    expect(
      readMarketBrowserQueryState(
        convertToParamMap({ sortBy: 'selectedPrice', sortDirection: 'desc' }),
      ),
    ).toMatchObject({
      sortBy: 'selectedPrice',
      sortDirection: 'desc',
    });
  });

  it('falls back to default sortBy for unknown columns', () => {
    expect(readMarketBrowserQueryState(convertToParamMap({ sortBy: 'not-a-column' })).sortBy).toBe(
      'itemName',
    );
  });

  it('round-trips non-default state through URL params', () => {
    const state = {
      ...defaultMarketBrowserQueryState,
      query: 'potion',
      sortBy: 'selectedPrice' as const,
      sortDirection: 'desc' as const,
      page: 2,
      qualityIds: [4],
    };

    const roundTripped = readMarketBrowserQueryState(
      convertToParamMap(toMarketBrowserQueryParams(state)),
    );

    expect(roundTripped).toMatchObject({
      query: 'potion',
      sortBy: 'selectedPrice',
      sortDirection: 'desc',
      page: 2,
      qualityIds: [4],
    });
  });

  it('reads filter params from the URL', () => {
    expect(
      readMarketBrowserQueryState(
        convertToParamMap({
          qualityIds: '4',
          minPrice: '100',
          recipeOnly: 'true',
        }),
      ),
    ).toMatchObject({
      qualityIds: [4],
      minPrice: 100,
      recipeOnly: true,
    });
  });

  it('omits default sort from serialized params', () => {
    expect(toMarketBrowserQueryParams(defaultMarketBrowserQueryState)).toMatchObject({
      sortBy: null,
      sortDirection: null,
      page: null,
      pageSize: null,
    });
  });

  it('defaults to backend first page', () => {
    expect(defaultMarketBrowserQueryState.page).toBe(0);
    expect(readMarketBrowserQueryState(convertToParamMap({})).page).toBe(0);
  });
});
