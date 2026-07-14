import { AuctionMarketFilter } from '@api/generated';
import { defaultCraftingBrowserQueryState } from '@core/mappers/crafting-browser-query.mapper';
import { toCraftingFilterSections } from '@core/mappers/crafting-filter-mapper';
import { describe, expect, it } from 'vitest';

describe('Crafting Filter Mapper', () => {
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
      {
        id: 'profit',
        label: 'Profit',
        type: 'MultiSelect' as unknown as AuctionMarketFilter.TypeEnum,
        min: null,
        max: null,
      },
    ] as const satisfies readonly AuctionMarketFilter[];

    const sections = toCraftingFilterSections(filters, defaultCraftingBrowserQueryState);

    expect(sections).toEqual([
      expect.objectContaining({ id: 'saleRatePercent', type: 'range' }),
      expect.objectContaining({ id: 'soldPerDay', type: 'range' }),
      expect.objectContaining({ id: 'profit', type: 'range' }),
    ]);
    expect(sections[0]!.label.length).toBeGreaterThan(0);
    expect(sections[1]!.label.length).toBeGreaterThan(0);
  });
});
