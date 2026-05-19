import { ChangeDetectionStrategy, Component } from '@angular/core';
import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import type Highcharts from 'highcharts/esm/highcharts';

import {
  ChartComponent,
  ChartPanelComponent,
  type ChartSeries,
  TooltipCardComponent,
  type TooltipRow,
} from '../../../public-api';
import { ChartPanelStoryHostComponent } from '../../support/story-hosts';

const meta: Meta = {
  title: 'Ethereal UI/Charts',
  parameters: { layout: 'centered' },
  decorators: [
    moduleMetadata({
      imports: [ChartComponent, ChartPanelComponent, TooltipCardComponent],
    }),
  ],
};

export default meta;

export const ChartSeriesPanel: StoryObj<ChartPanelStoryHostComponent> = {
  render: () => ({
    template: '<story-chart-panel-host />',
    moduleMetadata: {
      imports: [ChartPanelStoryHostComponent],
    },
  }),
};

@Component({
  selector: 'story-chart-panel-dashboard',
  imports: [ChartPanelComponent],
  template: `
    <div class="grid w-[980px] max-w-full gap-4 lg:grid-cols-2">
      <ee-chart-panel
        title="Daily market"
        rangeLabel="14 days"
        description="Quantity as bars with average price as a line."
        [series]="dailyMarketSeries"
        [seriesLabels]="{ quantity: 'Quantity', avgPrice: 'Average price' }"
      />
      <ee-chart-panel
        title="Crafting ROI"
        rangeLabel="14 days"
        description="Profit and ROI for a selected recipe."
        [series]="craftingSeries"
        [seriesLabels]="{ profit: 'Profit', roi: 'ROI' }"
        [options]="compactOptions"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class ChartPanelDashboardStoryComponent {
  protected readonly dailyMarketSeries = marketSeries(14);
  protected readonly craftingSeries: readonly ChartSeries[] = [
    {
      id: 'profit',
      kind: 'column',
      yScaleKey: 'gold',
      color: 'tertiary-container',
      points: Array.from({ length: 14 }, (_, x) => ({
        x,
        y: 420 + x * 28 + (x % 4) * 95,
      })),
    },
    {
      id: 'roi',
      kind: 'line',
      yScaleKey: 'roi',
      color: 'secondary',
      points: Array.from({ length: 14 }, (_, x) => ({
        x,
        y: 8 + (x % 5) * 1.4 + x * 0.15,
      })),
    },
  ];
  protected readonly compactOptions: Highcharts.Options = {
    yAxis: [{ labels: { format: '{value}g' } }, { labels: { format: '{value}%' } }],
  };
}

export const DashboardVisuals: StoryObj<ChartPanelDashboardStoryComponent> = {
  render: () => ({
    template: '<story-chart-panel-dashboard />',
    moduleMetadata: {
      imports: [ChartPanelDashboardStoryComponent],
    },
  }),
};

@Component({
  selector: 'story-chart-panel-options',
  imports: [ChartPanelComponent],
  template: `
    <div class="w-[760px] max-w-full">
      <ee-chart-panel
        title="Option overrides"
        rangeLabel="Last 24 hours"
        description="ChartSeries data with regular Highcharts options layered on top."
        [series]="series"
        [seriesLabels]="labels"
        [minPixelsPerCategory]="22"
        [options]="options"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class ChartPanelOptionsStoryComponent {
  protected readonly series = marketSeries(24);
  protected readonly labels = {
    quantity: 'Available quantity',
    avgPrice: 'Average unit price',
  };
  protected readonly options: Highcharts.Options = {
    chart: {
      spacing: [4, 16, 8, 8],
    },
    legend: {
      enabled: true,
      itemStyle: { color: 'var(--color-on-surface)' },
      itemHoverStyle: { color: 'var(--color-primary)' },
    },
    plotOptions: {
      column: {
        borderRadius: 3,
      },
      line: {
        marker: {
          enabled: true,
          radius: 3,
        },
      },
    },
    xAxis: {
      labels: {
        step: 3,
      },
    },
    yAxis: [
      {
        labels: { format: '{value}g' },
      },
      {
        labels: { format: '{value}' },
      },
    ],
  };
}

export const WithHighchartsOptions: StoryObj<ChartPanelOptionsStoryComponent> = {
  render: () => ({
    template: '<story-chart-panel-options />',
    moduleMetadata: {
      imports: [ChartPanelOptionsStoryComponent],
    },
  }),
};

@Component({
  selector: 'story-chart-panel-custom-tooltip',
  imports: [ChartPanelComponent, TooltipCardComponent],
  template: `
    <ng-template #tip let-ctx>
      <ee-tooltip-card
        [title]="'Hour ' + ctx.x"
        subtitle="Custom projected tooltip"
        [rows]="tooltipRows(ctx.valuesBySeriesId)"
        [compact]="true"
      />
    </ng-template>

    <div class="w-[760px] max-w-full">
      <ee-chart-panel
        title="Projected tooltip"
        rangeLabel="Last 18 hours"
        [series]="series"
        [tooltipTemplate]="tip"
        [options]="options"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class ChartPanelCustomTooltipStoryComponent {
  protected readonly series = marketSeries(18);
  protected readonly options: Highcharts.Options = {
    xAxis: {
      labels: {
        formatter() {
          return `${this.value}:00`;
        },
      },
    },
  };

  protected tooltipRows(
    valuesBySeriesId: Readonly<Record<string, number | undefined>>,
  ): TooltipRow[] {
    return Object.entries(valuesBySeriesId).map(([label, value]) => ({
      label,
      value: value === undefined ? undefined : Math.round(value).toLocaleString('en-US'),
    }));
  }
}

export const CustomTooltipTemplate: StoryObj<ChartPanelCustomTooltipStoryComponent> = {
  render: () => ({
    template: '<story-chart-panel-custom-tooltip />',
    moduleMetadata: {
      imports: [ChartPanelCustomTooltipStoryComponent],
    },
  }),
};

@Component({
  selector: 'story-raw-highcharts-panel',
  imports: [ChartPanelComponent],
  template: `
    <div class="w-[760px] max-w-full">
      <ee-chart-panel
        title="Raw Highcharts panel"
        rangeLabel="Native options"
        description="Panel chrome with a normal Highcharts options object."
        [options]="options"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class RawHighchartsPanelStoryComponent {
  protected readonly options: Highcharts.Options = {
    chart: { type: 'areaspline', height: 256 },
    legend: {
      enabled: true,
      itemStyle: { color: 'var(--color-on-surface)' },
    },
    tooltip: {
      enabled: true,
      shared: true,
    },
    xAxis: {
      categories: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
    },
    yAxis: {
      title: { text: undefined },
      labels: { format: '{value}k' },
    },
    plotOptions: {
      areaspline: {
        fillOpacity: 0.22,
        marker: { radius: 3 },
      },
    },
    series: [
      {
        type: 'areaspline',
        name: 'Region mean',
        color: 'var(--color-primary)',
        data: [18, 21, 17, 24, 29, 25, 31],
      },
      {
        type: 'areaspline',
        name: 'Realm mean',
        color: 'var(--color-tertiary)',
        data: [13, 16, 15, 19, 22, 21, 26],
      },
    ],
  };
}

export const RawHighchartsOptionsPanel: StoryObj<RawHighchartsPanelStoryComponent> = {
  render: () => ({
    template: '<story-raw-highcharts-panel />',
    moduleMetadata: {
      imports: [RawHighchartsPanelStoryComponent],
    },
  }),
};

@Component({
  selector: 'story-raw-chart',
  imports: [ChartComponent],
  template: `
    <div class="ee-glass w-[720px] max-w-full rounded-lg p-inner-padding">
      <ee-chart [options]="options" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class RawChartStoryComponent {
  protected readonly options: Highcharts.Options = {
    chart: {
      type: 'spline',
      height: 280,
    },
    legend: {
      enabled: true,
      itemStyle: { color: 'var(--color-on-surface)' },
    },
    tooltip: {
      enabled: true,
      valueSuffix: '%',
    },
    xAxis: {
      categories: ['00', '04', '08', '12', '16', '20'],
    },
    yAxis: {
      min: 0,
      title: { text: undefined },
      labels: { format: '{value}%' },
    },
    series: [
      {
        type: 'spline',
        name: 'Sale rate',
        color: 'var(--color-secondary)',
        data: [12, 14, 20, 18, 24, 29],
      },
      {
        type: 'spline',
        name: 'Craft margin',
        color: 'var(--color-primary)',
        data: [7, 9, 11, 16, 14, 21],
      },
    ],
  };
}

export const RawChartComponent: StoryObj<RawChartStoryComponent> = {
  render: () => ({
    template: '<story-raw-chart />',
    moduleMetadata: {
      imports: [RawChartStoryComponent],
    },
  }),
};

function marketSeries(count: number): readonly ChartSeries[] {
  const xs = Array.from({ length: count }, (_, i) => i);
  return [
    {
      id: 'quantity',
      kind: 'column',
      yScaleKey: 'quantity',
      color: 'tertiary-container',
      points: xs.map((x, i) => ({ x, y: 70 + i * 5 + (i % 4) * 18 })),
    },
    {
      id: 'avgPrice',
      kind: 'line',
      yScaleKey: 'gold',
      color: 'primary-container',
      points: xs.map((x, i) => ({ x, y: 1850 + i * 34 + (i % 5) * 90 })),
    },
  ];
}
