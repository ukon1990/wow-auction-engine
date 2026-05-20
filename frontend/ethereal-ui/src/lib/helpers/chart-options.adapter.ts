import type Highcharts from 'highcharts/esm/highcharts';

import {
  buildXDomain,
  buildYDomainsByKey,
  type ChartSeries,
  etherealColorVar,
  indexInXDomain,
} from './chart';

export interface ChartOptionsAdapterOptions {
  readonly minHeightPx?: number;
  readonly bandWidthFraction?: number;
}

export interface ChartOptionsAdapterResult {
  readonly options: Highcharts.Options;
  readonly xDomain: readonly number[];
}

export function chartSeriesToOptions(
  series: readonly ChartSeries[],
  adapterOptions: ChartOptionsAdapterOptions = {},
): ChartOptionsAdapterResult {
  const xDomain = buildXDomain(series);
  const yScaleKeys = [...new Set(series.map((s) => s.yScaleKey))];
  const yDomains = buildYDomainsByKey(series);
  const yAxis: Highcharts.YAxisOptions[] = yScaleKeys.map((key, index) => ({
    id: key,
    min: yDomains[key]?.min,
    max: yDomains[key]?.max,
    opposite: index % 2 === 1,
    title: { text: undefined },
  }));

  const options: Highcharts.Options = {
    chart: {
      height: adapterOptions.minHeightPx ?? 256,
      spacing: [8, 8, 12, 8],
    },
    xAxis: {
      categories: xDomain.map((x) => String(x)),
      crosshair: {
        color: 'rgba(255,255,255,0.12)',
        width: 1,
      },
    },
    yAxis,
    series: series.map((s): Highcharts.SeriesOptionsType => {
      const yAxisIndex = Math.max(0, yScaleKeys.indexOf(s.yScaleKey));
      const data = xDomain.map((x, categoryIndex) => {
        const point = s.points.find((p) => indexInXDomain(p.x, xDomain) === categoryIndex);
        return {
          x: categoryIndex,
          y: point?.y ?? null,
          custom: { domainX: x },
        };
      });
      const common = {
        id: s.id,
        name: s.id,
        color: etherealColorVar(s.color),
        data,
        yAxis: yAxisIndex,
      };
      return s.kind === 'column'
        ? {
            ...common,
            type: 'column',
            pointRange: 1,
            pointPadding: (1 - (adapterOptions.bandWidthFraction ?? 0.65)) / 2,
          }
        : {
            ...common,
            type: 'line',
          };
    }),
  };

  return { options, xDomain };
}

export function mergeHighchartsOptions(
  base: Highcharts.Options,
  overrides: Highcharts.Options | null | undefined,
): Highcharts.Options {
  if (!overrides) {
    return base;
  }
  return {
    ...base,
    ...overrides,
    chart: { ...base.chart, ...overrides.chart },
    credits: { ...base.credits, ...overrides.credits },
    legend: { ...base.legend, ...overrides.legend },
    plotOptions: { ...base.plotOptions, ...overrides.plotOptions },
    title: { ...base.title, ...overrides.title },
    tooltip: { ...base.tooltip, ...overrides.tooltip },
    xAxis: mergeXAxisOptions(base.xAxis, overrides.xAxis),
    yAxis: mergeYAxisOptions(base.yAxis, overrides.yAxis),
    series: overrides.series ?? base.series,
  };
}

function mergeXAxisOptions(
  base: Highcharts.XAxisOptions | Highcharts.XAxisOptions[] | undefined,
  overrides: Highcharts.XAxisOptions | Highcharts.XAxisOptions[] | undefined,
): Highcharts.XAxisOptions | Highcharts.XAxisOptions[] | undefined {
  if (!overrides) {
    return base;
  }
  if (Array.isArray(base) || Array.isArray(overrides)) {
    const baseArray = Array.isArray(base) ? base : base ? [base] : [];
    const overrideArray = Array.isArray(overrides) ? overrides : [overrides];
    return overrideArray.map((axis, index) => ({ ...baseArray[index], ...axis }));
  }
  return { ...base, ...overrides };
}

function mergeYAxisOptions(
  base: Highcharts.YAxisOptions | Highcharts.YAxisOptions[] | undefined,
  overrides: Highcharts.YAxisOptions | Highcharts.YAxisOptions[] | undefined,
): Highcharts.YAxisOptions | Highcharts.YAxisOptions[] | undefined {
  if (!overrides) {
    return base;
  }
  if (Array.isArray(base) || Array.isArray(overrides)) {
    const baseArray = Array.isArray(base) ? base : base ? [base] : [];
    const overrideArray = Array.isArray(overrides) ? overrides : [overrides];
    return overrideArray.map((axis, index) => ({ ...baseArray[index], ...axis }));
  }
  return { ...base, ...overrides };
}
