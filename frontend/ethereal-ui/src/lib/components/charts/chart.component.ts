import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { HighchartsChartComponent } from 'highcharts-angular';
import type Highcharts from 'highcharts/esm/highcharts';

import {
  chartSeriesToOptions,
  mergeHighchartsOptions,
  type ChartOptionsAdapterResult,
} from '../../helpers/chart-options.adapter';
import {
  type ChartCategoryHoverContext,
  type ChartSeries,
  valuesAtCategoryIndex,
} from '../../helpers/chart';

export type {
  ChartCategoryHoverContext,
  ChartPoint,
  ChartSeries,
  ChartSeriesKind,
  ChartXBandHoverContext,
  EtherealColorToken,
} from '../../helpers/chart';

@Component({
  selector: 'ee-chart',
  imports: [HighchartsChartComponent],
  template: `
    <div
      #chartRoot
      class="block h-full min-h-64 w-full"
      (pointerleave)="onPointerLeave()"
      (pointermove)="onPointerMove($event)"
    >
      <highcharts-chart
        class="block h-full min-h-64 w-full"
        [options]="adapterResult().options"
        [oneToOne]="true"
        (chartInstance)="onChartInstance($event)"
      />
    </div>
  `,
  host: {
    class: 'block h-full min-h-64 w-full',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChartComponent {
  readonly series = input<readonly ChartSeries[]>([]);
  readonly options = input<Highcharts.Options | null>(null);
  readonly minHeightPx = input(256);
  readonly bandWidthFraction = input(0.65);

  readonly categoryHover = output<ChartCategoryHoverContext>();
  readonly chartLeave = output<void>();
  readonly chartInstance = output<Highcharts.Chart>();

  private readonly chartRoot = viewChild<ElementRef<HTMLElement>>('chartRoot');
  private readonly chart = signal<Highcharts.Chart | null>(null);

  protected readonly adapterResult = computed((): ChartOptionsAdapterResult => {
    const result = chartSeriesToOptions(this.series(), {
      minHeightPx: this.minHeightPx(),
      bandWidthFraction: this.bandWidthFraction(),
    });
    return {
      ...result,
      options: mergeHighchartsOptions(result.options, this.options()),
    };
  });

  protected onChartInstance(chart: Highcharts.Chart): void {
    this.chart.set(chart);
    this.chartInstance.emit(chart);
  }

  protected onPointerMove(event: PointerEvent): void {
    const chart = this.chart();
    const root = this.chartRoot()?.nativeElement;
    const xDomain = this.adapterResult().xDomain;
    if (!chart || !root || xDomain.length === 0) {
      return;
    }

    const rect = root.getBoundingClientRect();
    const plotX = event.clientX - rect.left - chart.plotLeft;
    const categoryIndex = Math.round(chart.xAxis[0]?.toValue(plotX, true) ?? -1);
    if (categoryIndex < 0 || categoryIndex >= xDomain.length) {
      return;
    }

    this.categoryHover.emit({
      categoryIndex,
      x: xDomain[categoryIndex]!,
      valuesBySeriesId: valuesAtCategoryIndex(this.series(), xDomain, categoryIndex),
      event,
    });
  }

  protected onPointerLeave(): void {
    this.chartLeave.emit();
  }
}
