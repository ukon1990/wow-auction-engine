import { AuctionMarketFilter, AuctionMarketFilterResponse } from '@api/generated';
import { defaultMarketBrowserQueryState } from '@core/mappers/market-browser-query.mapper';
import { toFilterSections } from '@core/mappers/filter-mapper';
import { expect } from 'vitest';

describe('Filter Mapper', () => {
  it('toFilterSections', () => {
    const filter: AuctionMarketFilterResponse = {
      filters: [
        {
          id: 'price',
          label: 'Price',
          type: AuctionMarketFilter.TypeEnum.Range,
          min: null,
          max: null,
        },
        {
          id: 'quantity',
          label: 'Quantity',
          type: AuctionMarketFilter.TypeEnum.Range,
          min: null,
          max: null,
        },
        {
          id: 'qualityIds',
          label: 'Quality',
          type: AuctionMarketFilter.TypeEnum.MultiSelect,
          options: [
            {
              id: '1',
              label: 'Common',
              parentId: null,
            },
            {
              id: '4',
              label: 'Epic',
              parentId: null,
            },
          ],
          min: null,
          max: null,
        },
        {
          id: 'itemClassIds',
          label: 'Item Class',
          type: AuctionMarketFilter.TypeEnum.MultiSelect,
          options: [
            {
              id: '4',
              label: 'Armor',
              parentId: null,
            },
            {
              id: '0',
              label: 'Consumable',
              parentId: null,
            },
          ],
          min: null,
          max: null,
        },
        {
          id: 'recipeOnly',
          label: 'Has Recipe',
          type: AuctionMarketFilter.TypeEnum.Boolean,
          min: null,
          max: null,
        },
      ],
    };
    const sections = toFilterSections(filter.filters, defaultMarketBrowserQueryState);

    expect(sections.map((section) => section.id)).toEqual([
      'price',
      'quantity',
      'qualityIds',
      'itemClassIds',
      'recipeOnly',
    ]);
  });
});
