import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  signal,
  TemplateRef,
  viewChild,
} from '@angular/core';

import {
  buildXDomain,
  buildYDomainsByKey,
  ChartPoint,
  ChartSeries,
  ChartXBandHoverContext,
  columnRectsForSeries,
  DEFAULT_PLOT_MARGINS,
  etherealColorVar,
  EtherealColorToken,
  linePolylinePoints,
  svgContentWidthPx,
  valuesAtCategoryIndex,
  xBandHitRects,
  YDomain,
} from '../../helpers/chart';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';

export type {
  ChartPoint,
  ChartSeries,
  ChartXBandHoverContext,
  EtherealColorToken,
  ChartSeriesKind,
} from '../../helpers/chart';

@Component({
  selector: 'ee-chart-panel',
  imports: [NgTemplateOutlet, SymbolIconComponent],
  template: `
    <section
      #panelRoot
      class="ee-glass relative w-full min-w-0 overflow-visible rounded-lg p-inner-padding"
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
        [attr.aria-label]="scrollRegionLabel()"
      >
        <div
          class="relative min-h-64 h-64 border-b border-l border-white/10 pb-4 pl-4"
          [style.min-width]="'100%'"
          (pointerleave)="onPlotPointerLeave()"
        >
          <svg
            class="block h-full min-h-64 max-w-none"
            preserveAspectRatio="none"
            role="img"
            [attr.aria-label]="chartAriaLabel()"
            [attr.height]="'100%'"
            [attr.viewBox]="'0 0 100 100'"
            [attr.width]="svgWidthPx()"
          >
            @for (gy of gridHorizontalYs(); track gy) {
              <line
                [attr.x1]="margins.innerLeft"
                [attr.x2]="margins.innerRight"
                [attr.y1]="gy"
                [attr.y2]="gy"
                stroke="rgba(255,255,255,0.06)"
                stroke-dasharray="2 3"
                stroke-width="0.35"
              />
            }
            @for (layer of columnLayers(); track layer.id) {
              @for (r of layer.rects; track $index) {
                <rect
                  [attr.fill]="colorVar(layer.color)"
                  [attr.height]="r.height"
                  [attr.width]="r.width"
                  [attr.x]="r.x"
                  [attr.y]="r.y"
                  opacity="0.88"
                />
              }
            }
            @if (highlightBand(); as hb) {
              <rect
                class="pointer-events-none"
                fill="rgba(255,255,255,0.07)"
                stroke="rgba(255,255,255,0.18)"
                stroke-width="0.2"
                [attr.height]="hb.height"
                [attr.width]="hb.width"
                [attr.x]="hb.x"
                [attr.y]="hb.y"
              />
            }
            @for (layer of lineLayers(); track layer.id) {
              <polyline
                class="drop-shadow-[0_0_6px_rgba(236,185,19,0.35)]"
                fill="none"
                [attr.points]="layer.pointsAttr"
                [attr.stroke]="colorVar(layer.color)"
                stroke-width="2"
              />
            }
            @for (band of xBands(); track $index) {
              <rect
                class="cursor-crosshair"
                fill="transparent"
                [attr.height]="band.height"
                [attr.width]="band.width"
                [attr.x]="band.x"
                [attr.y]="band.y"
                (pointerenter)="onBandPointerEnter($event, $index)"
                (pointermove)="onBandPointerMove($event)"
              />
            }
          </svg>
        </div>
      </div>
      @if (tooltipBind(); as bind) {
        @if (tooltipPointerReady()) {
          <div
            class="pointer-events-none absolute z-[300] max-w-[min(18rem,85vw)]"
            [style.left.px]="tooltipLeftPx()"
            [style.top.px]="tooltipTopPx()"
          >
            <ng-container
              [ngTemplateOutlet]="bind.tpl"
              [ngTemplateOutletContext]="{ $implicit: bind.ctx }"
            />
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
  /** Gap between cursor and tooltip (CSS px in panel coordinates). */
  private static readonly TOOLTIP_OFFSET_X = 14;
  private static readonly TOOLTIP_OFFSET_Y = 14;

  private readonly panelRoot = viewChild.required<ElementRef<HTMLElement>>('panelRoot');

  readonly title = input.required<string>();
  readonly rangeLabel = input('14 days');
  /** When non-empty, drives the chart. */
  readonly series = input<readonly ChartSeries[]>([]);
  /** Legacy single line; used only when `series` is empty. */
  readonly points = input<readonly ChartPoint[] | undefined>(undefined);
  /** Accessible description; defaults are derived from `title` and series. */
  readonly description = input<string | undefined>(undefined);
  readonly minPixelsPerCategory = input(12);
  readonly minChartWidthPx = input(320);
  readonly bandWidthFraction = input(0.65);
  /**
   * Optional tooltip for the hovered x category. Receives `ChartXBandHoverContext` as `$implicit`.
   */
  readonly tooltipTemplate = input<TemplateRef<{ $implicit: ChartXBandHoverContext }> | null>(null);

  protected readonly margins = DEFAULT_PLOT_MARGINS;
  protected readonly colorVar = etherealColorVar;

  protected readonly hoveredCategoryIndex = signal<number | null>(null);
  /**
   * Tooltip position in the glass panel’s coordinate space (`absolute` under `backdrop-filter`,
   * not viewport — avoids mismatch with `clientX` / `clientY`).
   */
  protected readonly tooltipLeftPx = signal(0);
  protected readonly tooltipTopPx = signal(0);
  protected readonly tooltipPointerReady = signal(false);

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

  protected readonly xDomain = computed(() => buildXDomain(this.effectiveSeries()));

  protected readonly yDomains = computed(() => buildYDomainsByKey(this.effectiveSeries()));

  protected readonly svgWidthPx = computed(() =>
    svgContentWidthPx(this.xDomain().length, this.minPixelsPerCategory(), this.minChartWidthPx()),
  );

  protected readonly xBands = computed(() => xBandHitRects(this.xDomain()));

  protected readonly highlightBand = computed(() => {
    const i = this.hoveredCategoryIndex();
    const bands = this.xBands();
    if (i === null || i < 0 || i >= bands.length) {
      return null;
    }
    return bands[i]!;
  });

  protected readonly hoverContext = computed((): ChartXBandHoverContext | null => {
    const i = this.hoveredCategoryIndex();
    const xd = this.xDomain();
    if (i === null || xd.length === 0 || i >= xd.length) {
      return null;
    }
    const x = xd[i]!;
    return {
      categoryIndex: i,
      x,
      valuesBySeriesId: valuesAtCategoryIndex(this.effectiveSeries(), xd, i),
    };
  });

  protected readonly tooltipBind = computed(() => {
    const tpl = this.tooltipTemplate();
    const ctx = this.hoverContext();
    if (!tpl || !ctx) {
      return null;
    }
    return { tpl, ctx };
  });

  protected readonly gridHorizontalYs = computed(() => {
    const { innerTop, innerBottom } = DEFAULT_PLOT_MARGINS;
    const h = innerBottom - innerTop;
    return [1, 2, 3].map((i) => innerTop + (h * i) / 4);
  });

  protected readonly columnLayers = computed(() => {
    const series = this.effectiveSeries().filter((s) => s.kind === 'column');
    const xDomain = this.xDomain();
    const yDomains = this.yDomains();
    const frac = this.bandWidthFraction();
    return series
      .map((s) => {
        const domain = yDomains[s.yScaleKey];
        if (!domain) {
          return { id: s.id, color: s.color, rects: [] as const };
        }
        return {
          id: s.id,
          color: s.color,
          rects: columnRectsForSeries(s.points, xDomain, domain, frac),
        };
      })
      .filter((l) => l.rects.length > 0);
  });

  protected readonly lineLayers = computed(() => {
    const xDomain = this.xDomain();
    const yDomains = this.yDomains();
    return this.effectiveSeries()
      .filter((s) => s.kind === 'line')
      .map((s) => {
        const domain: YDomain = yDomains[s.yScaleKey] ?? { min: 0, max: 1 };
        return {
          id: s.id,
          color: s.color,
          pointsAttr: linePolylinePoints(s.points, xDomain, domain),
        };
      })
      .filter((l) => l.pointsAttr.length > 0);
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

  protected readonly scrollRegionLabel = computed(
    () => `${this.title()} chart, scroll horizontally for full history`,
  );

  protected onBandPointerEnter(event: PointerEvent, index: number): void {
    this.hoveredCategoryIndex.set(index);
    this.applyPointerForTooltip(event);
  }

  protected onBandPointerMove(event: PointerEvent): void {
    this.applyPointerForTooltip(event);
  }

  private applyPointerForTooltip(event: PointerEvent): void {
    const root = this.panelRoot().nativeElement;
    const r = root.getBoundingClientRect();
    this.tooltipLeftPx.set(event.clientX - r.left + ChartPanelComponent.TOOLTIP_OFFSET_X);
    this.tooltipTopPx.set(event.clientY - r.top + ChartPanelComponent.TOOLTIP_OFFSET_Y);
    this.tooltipPointerReady.set(true);
  }

  protected onPlotPointerLeave(): void {
    this.hoveredCategoryIndex.set(null);
    this.tooltipPointerReady.set(false);
  }
}
