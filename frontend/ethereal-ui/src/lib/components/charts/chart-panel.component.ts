import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  TemplateRef,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import type Highcharts from 'highcharts/esm/highcharts';

import {
  ChartComponent,
  type ChartCategoryHoverContext,
  type ChartPoint,
  type ChartSeries,
  type EtherealColorToken,
} from './chart.component';
import { svgContentWidthPx } from '../../helpers/chart';
import { TooltipOverlayService } from '../../services/tooltip-overlay.service';
import { TooltipCardComponent, type TooltipRow } from '../primitives/tooltip-card.component';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';

export type {
  ChartCategoryHoverContext,
  ChartPoint,
  ChartSeries,
  ChartSeriesKind,
  ChartXBandHoverContext,
  EtherealColorToken,
} from './chart.component';

@Component({
  selector: 'ee-chart-panel',
  imports: [ChartComponent, NgTemplateOutlet, SymbolIconComponent, TooltipCardComponent],
  template: `
    <section
      #panelRoot
      class="ee-glass relative w-full min-w-0 overflow-visible rounded-lg p-inner-padding"
      [style.z-index]="overlay.active() ? 500 : 0"
    >
      <div class="mb-6 flex items-center justify-between gap-4">
        <h2 class="ee-section-heading flex items-center gap-2 text-on-surface">
          <ee-symbol-icon class="text-outline" name="show_chart" />
          {{ title() }}
        </h2>
        <span class="ee-label text-outline">{{ rangeLabel() }}</span>
      </div>

      <div
        class="relative w-full min-w-0 overflow-x-auto overflow-y-hidden touch-pan-x"
        [attr.aria-label]="chartAriaLabel()"
      >
        <div class="h-64 min-h-64" [style.min-width.px]="chartWidthPx()">
          <ee-chart
            [series]="effectiveSeries()"
            [options]="options()"
            [minHeightPx]="256"
            [bandWidthFraction]="bandWidthFraction()"
            (categoryHover)="onCategoryHover($event)"
            (chartLeave)="onChartLeave()"
            (chartInstance)="chartInstance.emit($event)"
          />
        </div>
      </div>

      @if (overlay.active(); as position) {
        @if (hoverContext(); as ctx) {
          <div
            class="pointer-events-none absolute z-[300] max-w-[min(18rem,85vw)]"
            [style.left.px]="position.leftPx"
            [style.top.px]="position.topPx"
          >
            @if (tooltipTemplate(); as tpl) {
              <ng-container
                [ngTemplateOutlet]="tpl"
                [ngTemplateOutletContext]="{ $implicit: ctx }"
              />
            } @else {
              <ee-tooltip-card
                [title]="defaultTooltipTitle(ctx)"
                [rows]="defaultTooltipRows(ctx)"
                emptyMessage="No values for this category"
                [compact]="true"
              />
            }
          </div>
        }
      }
    </section>
  `,
  host: {
    class: 'block',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChartPanelComponent {
  private readonly panelRoot = viewChild.required<ElementRef<HTMLElement>>('panelRoot');
  protected readonly overlay = inject(TooltipOverlayService);

  readonly title = input.required<string>();
  readonly rangeLabel = input('14 days');
  readonly series = input<readonly ChartSeries[]>([]);
  readonly options = input<Highcharts.Options | null>(null);
  readonly points = input<readonly ChartPoint[] | undefined>(undefined);
  readonly description = input<string | undefined>(undefined);
  readonly minPixelsPerCategory = input(12);
  readonly minChartWidthPx = input(320);
  readonly bandWidthFraction = input(0.65);
  readonly seriesLabels = input<Readonly<Record<string, string>>>({});
  readonly tooltipTemplate = input<TemplateRef<{ $implicit: ChartCategoryHoverContext }> | null>(
    null,
  );
  readonly chartInstance = output<Highcharts.Chart>();

  protected readonly hoverContext = signal<ChartCategoryHoverContext | null>(null);

  protected readonly effectiveSeries = computed(() => {
    const s = this.series();
    if (s.length > 0) {
      return s;
    }
    const legacy = this.points();
    if (legacy && legacy.length > 0) {
      return [
        {
          id: 'default',
          kind: 'line' as const,
          yScaleKey: 'value',
          color: 'primary' as EtherealColorToken,
          points: legacy,
        },
      ];
    }
    return [];
  });

  protected readonly chartAriaLabel = computed(() => {
    const desc = this.description();
    if (desc) {
      return desc;
    }
    const n = this.effectiveSeries().length;
    if (n === 0) {
      return `${this.title()}: no data`;
    }
    return `${this.title()}: chart with ${n} series`;
  });

  protected readonly chartWidthPx = computed(() =>
    svgContentWidthPx(
      new Set(this.effectiveSeries().flatMap((s) => s.points.map((p) => p.x))).size,
      this.minPixelsPerCategory(),
      this.minChartWidthPx(),
    ),
  );

  protected onCategoryHover(ctx: ChartCategoryHoverContext): void {
    this.hoverContext.set(ctx);
    if (!ctx.event) {
      return;
    }
    this.overlay.showAtPointer(ctx.event, {
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

  protected defaultTooltipTitle(ctx: ChartCategoryHoverContext): string {
    return `${this.title()} · ${ctx.x}`;
  }

  protected defaultTooltipRows(ctx: ChartCategoryHoverContext): readonly TooltipRow[] {
    const labels = this.seriesLabels();
    return this.effectiveSeries()
      .map((s) => ({
        label: labels[s.id] ?? s.id,
        value: ctx.valuesBySeriesId[s.id],
      }))
      .filter((row) => row.value !== undefined);
  }
}
