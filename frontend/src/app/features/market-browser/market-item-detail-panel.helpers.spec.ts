import type Highcharts from 'highcharts/esm/highcharts';
import type {
  AuctionMarketItemCurrentListing,
  AuctionMarketItemDetailPoint,
  AuctionMarketItemDetailResponse,
  AuctionMarketItemDetailSummary,
  AuctionMarketItemHourlyPoint,
} from '@api/generated';
import type { ChartSeries } from '@ui';

import {
  activeScopePrice,
  activeScopePriceCaption,
  activeScopeQuantity,
  currentListingsPagination,
  dailyTooltipRows,
  hourlyTooltipRows,
  itemInfoRows,
  marketDetailChartOptions,
  marketDetailPanelContextsEqual,
  pagedCurrentListings,
  type ItemInfoLabels,
  type MarketDetailPanelContext,
} from './market-item-detail-panel.helpers';

describe('market item detail panel helpers', () => {
  it('calculates empty and upper-clamped pagination boundaries', () => {
    expect(currentListingsPagination(0, 5, 20)).toEqual({
      page: 0,
      pageSize: 20,
      totalItems: 0,
      totalPages: 0,
    });
    expect(currentListingsPagination(41, 9, 20)).toEqual({
      page: 2,
      pageSize: 20,
      totalItems: 41,
      totalPages: 3,
    });
  });

  it('returns only listings from the selected page', () => {
    const listings = Array.from(
      { length: 5 },
      (_, index) => ({ quantity: index + 1 }) as AuctionMarketItemCurrentListing,
    );

    expect(
      pagedCurrentListings(listings, currentListingsPagination(listings.length, 1, 2)).map(
        (listing) => listing.quantity,
      ),
    ).toEqual([3, 4]);
  });

  it('compares every context and variant field', () => {
    const context = panelContext();

    expect(marketDetailPanelContextsEqual(context, panelContext())).toBe(true);
    expect(
      marketDetailPanelContextsEqual(
        context,
        panelContext({ variant: { ...context.variant, bonusKey: 'different' } }),
      ),
    ).toBe(false);
    expect(marketDetailPanelContextsEqual(context, panelContext({ recipeId: 99 }))).toBe(false);
  });

  it('selects realm and commodity summary values and captions', () => {
    const summary: AuctionMarketItemDetailSummary = {
      selectedRealmPrice: 100,
      selectedRealmQuantity: 2,
      selectedRealmPriceChangePercent: -1.25,
      commodityPrice: 200,
      commodityQuantity: 4,
      commodityPriceChangePercent: 3.5,
    };

    expect(activeScopePrice(summary, 'realm')).toBe(100);
    expect(activeScopeQuantity(summary, 'realm')).toBe(2);
    expect(activeScopePriceCaption(summary, 'realm')).toContain('-1.3%');
    expect(activeScopePrice(summary, 'commodity')).toBe(200);
    expect(activeScopeQuantity(summary, 'commodity')).toBe(4);
    expect(activeScopePriceCaption(summary, 'commodity')).toContain('+3.5%');
  });

  it('routes chart labels by scale and provides x-axis labels', () => {
    const options = marketDetailChartOptions(
      [chartSeries('price'), chartSeries('quantity'), chartSeries('roi'), chartSeries('custom')],
      'en-US',
      { xLabelAt: (index) => `day-${index}` },
    );
    const axes = options.yAxis as Highcharts.YAxisOptions[];

    expect(formatAxisLabel(axes[0], 12345)).toBe('1g 23s 45c');
    expect(formatAxisLabel(axes[1], 1200)).toBe('1,200');
    expect(formatAxisLabel(axes[2], 12.5)).toBe('12.5%');
    expect(formatAxisLabel(axes[3], 7, 'fallback')).toBe('fallback');
    expect(formatXAxisLabel(options.xAxis as Highcharts.XAxisOptions, 1.2)).toBe('day-1');
  });

  it('builds empty and populated daily and hourly tooltip rows', () => {
    const numberDisplay = (value: number | null | undefined) => `number:${value ?? 'none'}`;

    expect(dailyTooltipRows(undefined, numberDisplay)).toEqual([]);
    expect(hourlyTooltipRows(undefined, numberDisplay)).toEqual([]);

    const dailyRows = dailyTooltipRows(dailyPoint(), numberDisplay);
    expect(dailyRows).toHaveLength(9);
    expect(dailyRows[0].value).toBe('2026-08-02');
    expect(dailyRows[1].value).toBe('number:8');
    expect(dailyRows[6].amount).toEqual({ gold: 1, silver: 23, copper: 45 });

    const hourlyRows = hourlyTooltipRows(hourlyPoint(), numberDisplay);
    expect(hourlyRows).toHaveLength(6);
    expect(hourlyRows[0].value).toBe('05:00');
    expect(hourlyRows[2].value).toBe('number:12');
  });

  it('builds placeholder and populated item information rows', () => {
    const labels = itemInfoLabels();
    expect(itemInfoRows(null, labels, String)).toEqual(
      Object.values(labels).map((label) => ({ label, value: '—' })),
    );

    const rows = itemInfoRows(itemDetail(), labels, (value) => `count:${value ?? 'none'}`);
    expect(rows).toEqual([
      { label: 'Quality', value: 'Epic' },
      { label: 'Class', value: 'Armor' },
      { label: 'Subclass', value: 'Plate' },
      { label: 'Expansion', value: 'Khaz Algar' },
      { label: 'Crafted by', value: 'count:3' },
      { label: 'Reagent in', value: 'count:7' },
    ]);
  });
});

function panelContext(overrides: Partial<MarketDetailPanelContext> = {}): MarketDetailPanelContext {
  return {
    region: 'eu',
    realmSlug: 'draenor',
    itemId: 123,
    variant: { bonusKey: '1', modifierKey: '2', petSpeciesId: 0 },
    initialScope: 'realm',
    recipeId: null,
    ...overrides,
  };
}

function chartSeries(yScaleKey: string): ChartSeries {
  return { id: yScaleKey, kind: 'line', yScaleKey, color: 'primary', points: [] };
}

function formatAxisLabel(axis: Highcharts.YAxisOptions, value: number, text?: string): string {
  const formatter = axis.labels?.formatter;
  expect(formatter).toBeTypeOf('function');
  const context = { value, text } as unknown as Highcharts.AxisLabelsFormatterContextObject;
  return formatter!.call(context, context);
}

function formatXAxisLabel(axis: Highcharts.XAxisOptions, value: number): string {
  const formatter = axis.labels?.formatter;
  expect(formatter).toBeTypeOf('function');
  const context = { value, text: '' } as unknown as Highcharts.AxisLabelsFormatterContextObject;
  return formatter!.call(context, context);
}

function dailyPoint(): AuctionMarketItemDetailPoint {
  return {
    statDate: '2026-08-02',
    pointTimestamp: '2026-08-02T00:00:00Z',
    minPrice: 10_000,
    p25Price: 11_000,
    avgPrice: 12_345,
    p75Price: 13_000,
    maxPrice: 14_000,
    minQuantity: 4,
    avgQuantity: 8,
    maxQuantity: 12,
  };
}

function hourlyPoint(): AuctionMarketItemHourlyPoint {
  return {
    timestamp: '2026-08-02T05:00:00Z',
    hourOfDay: 5,
    minPrice: 10_000,
    avgPrice: 12_345,
    maxPrice: 14_000,
    totalQuantity: 12,
  };
}

function itemInfoLabels(): ItemInfoLabels {
  return {
    quality: 'Quality',
    itemClass: 'Class',
    itemSubclass: 'Subclass',
    expansion: 'Expansion',
    craftedBy: 'Crafted by',
    reagentIn: 'Reagent in',
  };
}

function itemDetail(): AuctionMarketItemDetailResponse {
  return {
    item: {
      id: 123,
      name: 'Item',
      quality: { id: 4, name: ' Epic ' },
      itemClass: { id: 2, name: ' Armor ' },
      itemSubclass: { id: 3, name: ' Plate ' },
      expansion: { id: 11, name: ' Khaz Algar ' },
    },
    craftedByRecipeCount: 3,
    reagentInRecipeCount: 7,
  } as unknown as AuctionMarketItemDetailResponse;
}
