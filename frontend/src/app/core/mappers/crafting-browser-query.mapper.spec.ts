import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';

import {
  defaultCraftingBrowserQueryState,
  readCraftingBrowserQueryState,
  toCraftingBrowserQueryParams,
} from './crafting-browser-query.mapper';

describe('crafting-browser-query.mapper', () => {
  it('returns defaults for an empty query string', () => {
    expect(readCraftingBrowserQueryState(convertToParamMap({}))).toEqual(
      defaultCraftingBrowserQueryState,
    );
  });

  it('reads sort params from the URL', () => {
    expect(
      readCraftingBrowserQueryState(convertToParamMap({ sortBy: 'profit', sortDirection: 'desc' })),
    ).toMatchObject({
      sortBy: 'profit',
      sortDirection: 'desc',
    });
  });

  it('falls back to default sortBy for unknown columns', () => {
    expect(
      readCraftingBrowserQueryState(convertToParamMap({ sortBy: 'not-a-column' })).sortBy,
    ).toBe('itemName');
  });

  it('round-trips non-default state through URL params', () => {
    const state = {
      ...defaultCraftingBrowserQueryState,
      query: 'potion',
      sortBy: 'profit' as const,
      sortDirection: 'desc' as const,
      page: 1,
      professionIds: [171],
    };

    const roundTripped = readCraftingBrowserQueryState(
      convertToParamMap(toCraftingBrowserQueryParams(state)),
    );

    expect(roundTripped).toMatchObject({
      query: 'potion',
      sortBy: 'profit',
      sortDirection: 'desc',
      page: 1,
      professionIds: [171],
    });
  });

  it('reads filter params from the URL', () => {
    expect(
      readCraftingBrowserQueryState(
        convertToParamMap({
          professionIds: '171',
          minProfit: '100',
          requireCompleteReagentPricing: 'true',
        }),
      ),
    ).toMatchObject({
      professionIds: [171],
      minProfit: 100,
      requireCompleteReagentPricing: true,
    });
  });

  it('reads expansionIds from the URL', () => {
    expect(
      readCraftingBrowserQueryState(
        convertToParamMap({
          expansionIds: ['1', '3'],
        }),
      ).expansionIds,
    ).toEqual([1, 3]);
  });

  it('omits default sort and page from serialized params', () => {
    expect(toCraftingBrowserQueryParams(defaultCraftingBrowserQueryState)).toMatchObject({
      sortBy: null,
      sortDirection: null,
      page: null,
      pageSize: null,
    });
  });

  it('clamps page size to max', () => {
    expect(readCraftingBrowserQueryState(convertToParamMap({ pageSize: '500' })).pageSize).toBe(
      200,
    );
  });
});
