import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  TemplateRef,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import type Highcharts from 'highcharts/esm/highcharts';

import { TooltipOverlayService } from '../../services/tooltip-overlay.service';
import { ChartComponent } from '../charts/chart.component';
import { TooltipCardComponent, type TooltipRow } from '../primitives/tooltip-card.component';

export interface HeatmapCell {
  readonly row: number;
  readonly col: number;
  readonly value: number | null | undefined;
  readonly label?: string;
}

export interface HeatmapTooltipContext {
  readonly cell: HeatmapCell;
  readonly rowLabel: string;
  readonly columnLabel: string;
}

export interface HeatmapTooltipTemplateContext extends HeatmapTooltipContext {
  readonly $implicit: HeatmapTooltipContext;
}

@Component({
  selector: 'ee-heatmap-grid',
  imports: [ChartComponent, NgTemplateOutlet, TooltipCardComponent],
  template: `
    <div
      #panelRoot
      class="ee-glass relative overflow-visible rounded-lg p-inner-padding"
      [style.z-index]="overlay.active() ? 500 : 0"
    >
      <div class="mb-4 flex items-center justify-between gap-4">
        <h2 class="ee-section-heading text-on-surface">{{ title() }}</h2>
        @if (rangeLabel()) {
          <span class="ee-label text-outline">{{ rangeLabel() }}</span>
        }
      </div>
      <div class="w-full min-w-0 overflow-visible">
        <div
          #chartRoot
          class="w-full min-w-0"
          role="img"
          [attr.aria-label]="ariaLabel()"
          (pointermove)="onPointerMove($event)"
          (pointerleave)="onChartLeave()"
        >
          <ee-chart
            [options]="heatmapOptions()"
            [minHeightPx]="chartHeightPx()"
            (chartInstance)="chart.set($event)"
          />
        </div>
      </div>
      @if (overlay.active(); as position) {
        @if (hoverContext(); as ctx) {
          <div
            class="pointer-events-none absolute z-[1000] max-w-[min(18rem,85vw)]"
            [style.left.px]="position.leftPx"
            [style.top.px]="position.topPx"
          >
            @if (tooltipTemplate(); as tpl) {
              <ng-container
                [ngTemplateOutlet]="tpl"
                [ngTemplateOutletContext]="{
                  $implicit: ctx,
                  cell: ctx.cell,
                  rowLabel: ctx.rowLabel,
                  columnLabel: ctx.columnLabel,
                }"
              />
            } @else {
              <ee-tooltip-card
                [title]="defaultTooltipTitle(ctx)"
                [rows]="defaultTooltipRows(ctx)"
                emptyMessage="No values for this cell"
                [compact]="true"
              />
            }
          </div>
        }
      }
      @if (description()) {
        <p class="ee-label mt-3 text-outline">{{ description() }}</p>
      }
      <div class="sr-only" role="grid" [attr.aria-label]="ariaLabel()">
        @for (label of columnLabels(); track $index) {
          <span role="columnheader">{{ label }}</span>
        }
        @let columns = columnLabels();
        @for (row of gridRows(); track row.index) {
          <span role="rowheader">{{ row.label }}</span>
          @for (cell of row.cells; track cell.col) {
            <span role="gridcell">{{ cellAriaLabel(row.label, columns[cell.col], cell) }}</span>
          }
        }
      </div>
    </div>
  `,
  host: {
    class: 'relative block min-w-0 max-w-full',
    '[style.z-index]': 'overlay.active() ? 1000 : 0',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeatmapGridComponent {
  private readonly panelRoot = viewChild.required<ElementRef<HTMLElement>>('panelRoot');
  private readonly chartRoot = viewChild.required<ElementRef<HTMLElement>>('chartRoot');
  protected readonly overlay = inject(TooltipOverlayService);

  readonly title = input('Heatmap');
  readonly rangeLabel = input('');
  readonly description = input('');
  readonly rowLabels = input<readonly string[]>([]);
  readonly columnLabels = input<readonly string[]>([]);
  readonly cells = input<readonly HeatmapCell[]>([]);
  readonly min = input<number | null>(null);
  readonly max = input<number | null>(null);
  readonly tooltipTemplate = input<TemplateRef<HeatmapTooltipTemplateContext> | null>(null);

  protected readonly chart = signal<Highcharts.Chart | null>(null);
  protected readonly hoverContext = signal<HeatmapTooltipContext | null>(null);

  protected readonly gridRows = computed(() => {
    const byKey = new Map(this.cells().map((cell) => [`${cell.row}:${cell.col}`, cell]));
    return this.rowLabels().map((label, rowIndex) => ({
      index: rowIndex,
      label,
      cells: this.columnLabels().map(
        (_, colIndex) =>
          byKey.get(`${rowIndex}:${colIndex}`) ?? { row: rowIndex, col: colIndex, value: null },
      ),
    }));
  });

  private readonly domain = computed(() => {
    const values = this.cells()
      .map((cell) => cell.value)
      .filter((v): v is number => v != null && Number.isFinite(v));
    const min = this.min() ?? Math.min(0, ...values);
    const max = this.max() ?? Math.max(0, ...values);
    return { min, max: max === min ? min + 1 : max };
  });

  protected readonly chartHeightPx = computed(() =>
    Math.max(256, 96 + Math.max(1, this.rowLabels().length) * 34),
  );

  protected readonly heatmapOptions = computed<Highcharts.Options>(() => {
    const columns = this.columnLabels();
    const rows = this.rowLabels();
    const cells = this.gridRows().flatMap((row) =>
      row.cells.map((cell) => ({
        x: cell.col,
        y: cell.row,
        value: this.validValue(cell.value) ? cell.value : null,
        custom: {
          cell,
          rowLabel: row.label,
          columnLabel: columns[cell.col] ?? '',
          label: cell.label ?? this.valueLabel(cell.value),
        },
      })),
    );
    const { min, max } = this.domain();

    return {
      chart: {
        type: 'heatmap',
        height: this.chartHeightPx(),
        spacing: [8, 8, 12, 8],
      },
      colorAxis: {
        min,
        max,
        minColor: 'rgba(255,255,255,0.04)',
        stops: [
          [0, 'hsla(8, 75%, 48%, 0.2)'],
          [0.5, 'hsla(76, 75%, 48%, 0.48)'],
          [1, 'hsla(143, 75%, 48%, 0.75)'],
        ],
      },
      legend: { enabled: false },
      tooltip: {
        enabled: false,
      },
      xAxis: {
        categories: [...columns],
        tickLength: 0,
        title: { text: undefined },
      },
      yAxis: {
        categories: [...rows],
        reversed: true,
        title: { text: undefined },
      },
      plotOptions: {
        series: {
          animation: false,
          borderColor: 'rgba(255,255,255,0.1)',
          borderWidth: 1,
          states: {
            inactive: { opacity: 1 },
          },
        },
      },
      series: [
        {
          type: 'heatmap',
          name: this.title(),
          nullColor: 'rgba(255,255,255,0.04)',
          borderRadius: 3,
          data: cells,
        } as Highcharts.SeriesOptionsType,
      ],
    };
  });

  protected ariaLabel(): string {
    return $localize`:@@heatmap.gridAria:${this.title()} grid`;
  }

  protected valueLabel(value: number | null | undefined): string {
    return value == null || !Number.isFinite(value)
      ? $localize`:@@heatmap.noSamples:No samples`
      : value.toLocaleString(undefined, { maximumFractionDigits: 1 });
  }

  protected cellAriaLabel(rowLabel: string, columnLabel: string, cell: HeatmapCell): string {
    return `${rowLabel} ${columnLabel}: ${cell.label ?? this.valueLabel(cell.value)}`;
  }

  protected defaultTooltipTitle(ctx: HeatmapTooltipContext): string {
    return `${this.title()} · ${ctx.rowLabel} · ${ctx.columnLabel}`;
  }

  protected defaultTooltipRows(ctx: HeatmapTooltipContext): readonly TooltipRow[] {
    return [{ label: ctx.cell.label ?? this.valueLabel(ctx.cell.value) }];
  }

  protected onPointerMove(event: PointerEvent): void {
    const chart = this.chart();
    if (!chart) {
      return;
    }

    const rootRect = this.chartRoot().nativeElement.getBoundingClientRect();
    const plotX = event.clientX - rootRect.left - chart.plotLeft;
    const plotY = event.clientY - rootRect.top - chart.plotTop;
    const col = Math.round(chart.xAxis[0]?.toValue(plotX, true) ?? -1);
    const row = Math.round(chart.yAxis[0]?.toValue(plotY, true) ?? -1);
    const rowModel = this.gridRows()[row];
    const cell = rowModel?.cells[col];
    const columnLabel = this.columnLabels()[col];
    if (!rowModel || !cell || columnLabel === undefined) {
      this.onChartLeave();
      return;
    }

    const rowLabel = rowModel.label;
    this.hoverContext.set({ cell, rowLabel, columnLabel });
    this.overlay.showAtPointer(event, {
      mode: 'absolute',
      anchor: this.panelRoot().nativeElement,
      mouseOffsetX: 14,
      mouseOffsetY: 14,
    });
  }

  protected onChartLeave(): void {
    this.hoverContext.set(null);
    this.overlay.clear();
  }

  private validValue(value: number | null | undefined): value is number {
    return value != null && Number.isFinite(value);
  }
}
