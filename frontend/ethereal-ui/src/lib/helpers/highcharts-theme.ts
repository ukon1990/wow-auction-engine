import { EnvironmentProviders } from '@angular/core';
import { provideHighcharts } from 'highcharts-angular';
import type Highcharts from 'highcharts';

export function provideHighchartsTheme(): EnvironmentProviders {
  return provideHighcharts({
    instance: async () => {
      const highcharts = await import('highcharts/esm/highcharts');
      await import('highcharts/esm/modules/heatmap');
      return ('default' in highcharts
        ? highcharts.default
        : highcharts) as unknown as typeof Highcharts;
    },
    options: highchartsThemeOptions(),
  });
}

export function highchartsThemeOptions(): Highcharts.Options {
  return {
    chart: {
      backgroundColor: 'transparent',
      style: {
        fontFamily: 'var(--font-space-grotesk)',
      },
    },
    credits: { enabled: false },
    legend: { enabled: false },
    title: { text: undefined },
    tooltip: { enabled: false },
    xAxis: {
      gridLineColor: 'rgba(255,255,255,0.06)',
      labels: { style: { color: 'var(--color-outline)' } },
      lineColor: 'rgba(255,255,255,0.12)',
      tickColor: 'rgba(255,255,255,0.12)',
    },
    yAxis: {
      gridLineColor: 'rgba(255,255,255,0.06)',
      labels: { style: { color: 'var(--color-outline)' } },
      title: { text: undefined },
    },
    plotOptions: {
      column: {
        borderWidth: 0,
        groupPadding: 0.12,
        pointPadding: 0.08,
      },
      line: {
        marker: { enabled: false },
        lineWidth: 2,
      },
      series: {
        animation: false,
        states: {
          inactive: { opacity: 1 },
        },
      },
    },
  };
}
