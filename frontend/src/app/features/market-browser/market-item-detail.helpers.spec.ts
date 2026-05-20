import {
  dailyPointsToChartSeries,
  dayOfMonthLabel,
  dayOfWeekFromTimestamp,
  hourOfDayLabel,
  hourlyPriceHeatmapCellsFromPoints,
  quantityAxisLabel,
  shouldFallbackToCommodityFetch,
} from './market-item-detail.helpers';

describe('market-item-detail helpers', () => {
  it('falls back to commodity fetch when both scopes are empty', () => {
    const detail = {
      regionalMetricsRedundant: false,
      summary: {
        selectedRealmPrice: null,
        selectedRealmQuantity: null,
        commodityPrice: null,
        commodityQuantity: null,
      },
    } as never;

    expect(shouldFallbackToCommodityFetch(detail)).toBe(true);
  });

  it('does not duplicate overlapping daily price lines', () => {
    const series = dailyPointsToChartSeries([
      {
        avgQuantity: 10,
        minPrice: 100,
        avgPrice: 100,
        maxPrice: 100,
        p25Price: null,
        p75Price: null,
      },
    ] as never);

    expect(series.map((entry) => entry.id)).toEqual(['quantity', 'low']);
  });

  it('aggregates hourly price heatmap cells by weekday/hour', () => {
    const cells = hourlyPriceHeatmapCellsFromPoints([
      { timestamp: '2026-05-04T10:00:00Z', hourOfDay: 10, avgPrice: 100 },
      { timestamp: '2026-05-04T10:30:00Z', hourOfDay: 10, avgPrice: 200 },
      { timestamp: '2026-05-04T11:00:00Z', hourOfDay: 11, avgPrice: 300 },
    ] as never);

    const mondayTen = cells.find((c) => c.row === 0 && c.col === 10);
    expect(mondayTen?.value).toBe(150);
    expect(cells.length).toBe(2);
  });

  it('maps UTC sunday to row 6', () => {
    expect(dayOfWeekFromTimestamp('2026-05-10T00:00:00Z')).toBe(6);
  });

  it('formats chart axis date and hour labels', () => {
    expect(dayOfMonthLabel('2026-05-19')).toBe('19');
    expect(dayOfMonthLabel('2026-05-09T22:00:00Z')).toBe('9');
    expect(hourOfDayLabel(7)).toBe('07:00');
    expect(hourOfDayLabel(23)).toBe('23:00');
  });

  it('formats quantity axis labels as readable counts', () => {
    expect(quantityAxisLabel(1234)).toBe('1,234');
    expect(quantityAxisLabel(0.5)).toBe('0.5');
    expect(quantityAxisLabel(12500)).toBe('12.5K');
  });
});
