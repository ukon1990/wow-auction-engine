import { chartSeriesToOptions } from './chart-options.adapter';

describe('chart options adapter', () => {
  it('maps ChartSeries to Highcharts axes and series', () => {
    const { options, xDomain } = chartSeriesToOptions([
      {
        id: 'price',
        kind: 'line',
        yScaleKey: 'gold',
        color: 'primary',
        points: [
          { x: 2, y: 20 },
          { x: 1, y: 10 },
        ],
      },
      {
        id: 'quantity',
        kind: 'column',
        yScaleKey: 'qty',
        color: 'tertiary',
        points: [{ x: 2, y: 4 }],
      },
    ]);

    expect(xDomain).toEqual([1, 2]);
    expect(options.xAxis).toEqual(
      expect.objectContaining({
        categories: ['1', '2'],
      }),
    );
    expect(options.series).toHaveLength(2);
    expect(options.series?.[0]).toEqual(
      expect.objectContaining({
        id: 'price',
        type: 'line',
        color: 'var(--color-primary)',
      }),
    );
    expect(options.series?.[1]).toEqual(
      expect.objectContaining({
        id: 'quantity',
        type: 'column',
        color: 'var(--color-tertiary)',
      }),
    );
  });
});
