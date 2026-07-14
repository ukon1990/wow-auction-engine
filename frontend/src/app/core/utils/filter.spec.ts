import { defaultCraftingBrowserQueryState } from '@core/mappers/crafting-browser-query.mapper';
import { defaultMarketBrowserQueryState } from '@core/mappers/market-browser-query.mapper';
import {
  applyCraftingFilterToggle,
  applyCraftingRangeFilter,
  applyMarketFilterSelect,
  applyMarketFilterToggle,
  applyMarketRangeFilter,
  parseFilterOptionId,
  toggleNumberInList,
  toQuality,
} from '@core/utils/filter';
import { describe, expect, it } from 'vitest';

describe('filter helpers', () => {
  describe('toQuality', () => {
    it('fallback to common if unknown value', () => expect(toQuality('unknown')).toBe('common'));
    it('returns the input value if valid', () => expect(toQuality('epic')).toBe('epic'));
  });

  describe('parseFilterOptionId', () => {
    it('parses two-part ids', () => {
      expect(parseFilterOptionId('qualityIds:4')).toEqual({ filterId: 'qualityIds', value: 4 });
    });

    it('parses three-part subclass ids', () => {
      expect(parseFilterOptionId('itemSubclassIds:4:12')).toEqual({
        filterId: 'itemSubclassIds',
        parentId: 4,
        value: 12,
      });
    });
  });

  describe('toggleNumberInList', () => {
    it('adds and removes ids', () => {
      expect(toggleNumberInList([1], 2)).toEqual([1, 2]);
      expect(toggleNumberInList([1, 2], 2)).toEqual([1]);
    });
  });

  describe('applyMarketFilterToggle', () => {
    it('toggles quality ids', () => {
      const state = { ...defaultMarketBrowserQueryState, qualityIds: [4] };
      expect(applyMarketFilterToggle(state, 'qualityIds:4').qualityIds).toEqual([]);
      expect(applyMarketFilterToggle(state, 'qualityIds:3').qualityIds).toEqual([4, 3]);
    });

    it('toggles recipeOnly', () => {
      expect(
        applyMarketFilterToggle(defaultMarketBrowserQueryState, 'recipeOnly:true').recipeOnly,
      ).toBe(true);
      expect(
        applyMarketFilterToggle(
          { ...defaultMarketBrowserQueryState, recipeOnly: true },
          'recipeOnly:true',
        ).recipeOnly,
      ).toBeNull();
    });

    it('clears subclasses when a class is deselected', () => {
      const state = {
        ...defaultMarketBrowserQueryState,
        itemClassIds: [4],
        itemSubclassIds: [12],
      };
      expect(applyMarketFilterToggle(state, 'itemClassIds:4').itemSubclassIds).toEqual([]);
    });
  });

  describe('applyMarketFilterSelect', () => {
    it('clears select filters', () => {
      expect(
        applyMarketFilterSelect(
          { ...defaultMarketBrowserQueryState, itemClassIds: [4], itemSubclassIds: [12] },
          'itemClassIds',
          null,
        ),
      ).toMatchObject({ itemClassIds: [], itemSubclassIds: [] });
    });

    it('sets a single class and clears subclasses', () => {
      expect(
        applyMarketFilterSelect(defaultMarketBrowserQueryState, 'itemClassIds', 'itemClassIds:4'),
      ).toMatchObject({ itemClassIds: [4], itemSubclassIds: [] });
    });

    it('sets subclass with parent class', () => {
      expect(
        applyMarketFilterSelect(
          defaultMarketBrowserQueryState,
          'itemSubclassIds',
          'itemSubclassIds:4:12',
        ),
      ).toMatchObject({ itemClassIds: [4], itemSubclassIds: [12] });
    });
  });

  describe('applyMarketRangeFilter', () => {
    it('updates min and max price', () => {
      const withMin = applyMarketRangeFilter(defaultMarketBrowserQueryState, 'price', 'min', 100);
      expect(withMin.minPrice).toBe(100);
      expect(applyMarketRangeFilter(withMin, 'price', 'max', 500).maxPrice).toBe(500);
    });

    it('updates min sale rate percent and sold per day', () => {
      const withSaleRate = applyMarketRangeFilter(
        defaultMarketBrowserQueryState,
        'saleRatePercent',
        'min',
        25,
      );
      expect(withSaleRate.minSaleRatePercent).toBe(25);
      const withSoldPerDay = applyMarketRangeFilter(withSaleRate, 'soldPerDay', 'min', 1.5);
      expect(withSoldPerDay.minSoldPerDay).toBe(1.5);
    });
  });

  describe('applyCraftingFilterToggle', () => {
    it('toggles profession ids', () => {
      const state = { ...defaultCraftingBrowserQueryState, professionIds: [171] };
      expect(applyCraftingFilterToggle(state, 'professionIds:171').professionIds).toEqual([]);
      expect(applyCraftingFilterToggle(state, 'professionIds:164').professionIds).toEqual([
        171, 164,
      ]);
    });

    it('toggles requireCompleteReagentPricing', () => {
      expect(
        applyCraftingFilterToggle(
          defaultCraftingBrowserQueryState,
          'requireCompleteReagentPricing:true',
        ).requireCompleteReagentPricing,
      ).toBe(true);
      expect(
        applyCraftingFilterToggle(
          { ...defaultCraftingBrowserQueryState, requireCompleteReagentPricing: true },
          'requireCompleteReagentPricing:true',
        ).requireCompleteReagentPricing,
      ).toBe(false);
    });
  });

  describe('applyCraftingRangeFilter', () => {
    it('updates min and max profit', () => {
      const withMin = applyCraftingRangeFilter(
        defaultCraftingBrowserQueryState,
        'profit',
        'min',
        100,
      );
      expect(withMin.minProfit).toBe(100);
      expect(applyCraftingRangeFilter(withMin, 'profit', 'max', 500).maxProfit).toBe(500);
    });

    it('updates min sale rate percent and sold per day', () => {
      const withSaleRate = applyCraftingRangeFilter(
        defaultCraftingBrowserQueryState,
        'saleRatePercent',
        'min',
        25,
      );
      expect(withSaleRate.minSaleRatePercent).toBe(25);
      const withSoldPerDay = applyCraftingRangeFilter(withSaleRate, 'soldPerDay', 'max', 10);
      expect(withSoldPerDay.maxSoldPerDay).toBe(10);
    });
  });
});
