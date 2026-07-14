import { AuctionMarketFilter, AuctionMarketFilterResponse } from '@api/generated';
import { defaultMarketBrowserQueryState } from '@core/mappers/market-browser-query.mapper';
import { toFilterSections } from '@core/mappers/filter-mapper';
import { filterType } from '@core/utils/filter';
import { describe, expect, it } from 'vitest';

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

  it('forces range type for TSM filters when API type is wrong or missing', () => {
    const filters = [
      {
        id: 'saleRatePercent',
        label: 'Sale rate %',
        type: 'RANGE' as unknown as AuctionMarketFilter.TypeEnum,
        min: null,
        max: null,
      },
      {
        id: 'soldPerDay',
        label: 'Avg sold/day',
        type: undefined as unknown as AuctionMarketFilter.TypeEnum,
        min: null,
        max: null,
      },
    ] as const satisfies readonly AuctionMarketFilter[];

    expect(filterType(filters[0]!)).toBe('range');
    expect(filterType(filters[1]!)).toBe('range');

    const sections = toFilterSections(filters, defaultMarketBrowserQueryState);
    expect(sections).toEqual([
      expect.objectContaining({
        id: 'saleRatePercent',
        type: 'range',
        label: expect.any(String),
      }),
      expect.objectContaining({
        id: 'soldPerDay',
        type: 'range',
        label: expect.any(String),
      }),
    ]);
    expect(sections[0]!.label.length).toBeGreaterThan(0);
    expect(sections[1]!.label.length).toBeGreaterThan(0);
  });
});
