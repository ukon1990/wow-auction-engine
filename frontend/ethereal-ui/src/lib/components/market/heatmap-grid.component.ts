import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, TemplateRef } from '@angular/core';

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

@Component({
  selector: 'ee-heatmap-grid',
  imports: [NgTemplateOutlet],
  template: `
    <div class="ee-glass rounded-lg p-inner-padding">
      <div class="mb-4 flex items-center justify-between gap-4">
        <h2 class="ee-section-heading text-on-surface">{{ title() }}</h2>
        @if (rangeLabel()) {
          <span class="ee-label text-outline">{{ rangeLabel() }}</span>
        }
      </div>
      <div class="overflow-x-auto">
        <div
          class="grid min-w-[42rem] gap-1"
          role="grid"
          [attr.aria-label]="ariaLabel()"
          [style.grid-template-columns]="gridTemplateColumns()"
        >
          <div aria-hidden="true"></div>
          @for (label of columnLabels(); track $index) {
            <div class="ee-label text-center text-outline" role="columnheader">{{ label }}</div>
          }
          @for (row of gridRows(); track row.index) {
            <div class="ee-label flex items-center text-outline" role="rowheader">{{ row.label }}</div>
            @for (cell of row.cells; track cell.col) {
              <div
                class="group relative h-8 rounded border border-white/10 transition focus-within:ring-2 focus-within:ring-primary/60"
                role="gridcell"
                [style.background]="cellBackground(cell.value)"
                [attr.aria-label]="cellAriaLabel(row.label, columnLabels()[cell.col], cell)"
                tabindex="0"
              >
                <span class="sr-only">{{ cellAriaLabel(row.label, columnLabels()[cell.col], cell) }}</span>
                <div
                  class="pointer-events-none absolute bottom-full left-1/2 z-20 mb-2 hidden min-w-44 -translate-x-1/2 rounded border border-white/15 bg-surface-container/95 px-2 py-1.5 text-xs text-on-surface shadow-lg backdrop-blur group-hover:block group-focus-within:block"
                >
                  @if (tooltipTemplate()) {
                    <ng-container
                      [ngTemplateOutlet]="tooltipTemplate()"
                      [ngTemplateOutletContext]="{ cell: cell, rowLabel: row.label, columnLabel: columnLabels()[cell.col] }"
                    />
                  } @else {
                    <div class="ee-label text-outline">{{ row.label }} · {{ columnLabels()[cell.col] }}</div>
                    <div class="font-space-mono">{{ cell.label ?? valueLabel(cell.value) }}</div>
                  }
                </div>
              </div>
            }
          }
        </div>
      </div>
      @if (description()) {
        <p class="ee-label mt-3 text-outline">{{ description() }}</p>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeatmapGridComponent {
  readonly title = input('Heatmap');
  readonly rangeLabel = input('');
  readonly description = input('');
  readonly rowLabels = input<readonly string[]>([]);
  readonly columnLabels = input<readonly string[]>([]);
  readonly cells = input<readonly HeatmapCell[]>([]);
  readonly min = input<number | null>(null);
  readonly max = input<number | null>(null);
  readonly tooltipTemplate = input<TemplateRef<HeatmapTooltipContext> | null>(null);

  protected readonly gridTemplateColumns = computed(() => `minmax(4rem, 7rem) repeat(${this.columnLabels().length}, minmax(1.4rem, 1fr))`);

  protected readonly gridRows = computed(() => {
    const byKey = new Map(this.cells().map((cell) => [`${cell.row}:${cell.col}`, cell]));
    return this.rowLabels().map((label, rowIndex) => ({
      index: rowIndex,
      label,
      cells: this.columnLabels().map((_, colIndex) => byKey.get(`${rowIndex}:${colIndex}`) ?? { row: rowIndex, col: colIndex, value: null }),
    }));
  });

  private readonly domain = computed(() => {
    const values = this.cells().map((cell) => cell.value).filter((v): v is number => v != null && Number.isFinite(v));
    const min = this.min() ?? Math.min(0, ...values);
    const max = this.max() ?? Math.max(0, ...values);
    return { min, max: max === min ? min + 1 : max };
  });

  protected ariaLabel(): string {
    return `${this.title()} grid`;
  }

  protected cellBackground(value: number | null | undefined): string {
    if (value == null || !Number.isFinite(value)) return 'rgba(255,255,255,0.04)';
    const { min, max } = this.domain();
    const t = Math.max(0, Math.min(1, (value - min) / (max - min)));
    const hue = 8 + t * 135;
    const alpha = 0.2 + t * 0.55;
    return `hsla(${hue}, 75%, 48%, ${alpha})`;
  }

  protected valueLabel(value: number | null | undefined): string {
    return value == null || !Number.isFinite(value) ? 'No samples' : value.toLocaleString('en-US', { maximumFractionDigits: 1 });
  }

  protected cellAriaLabel(rowLabel: string, columnLabel: string, cell: HeatmapCell): string {
    return `${rowLabel} ${columnLabel}: ${cell.label ?? this.valueLabel(cell.value)}`;
  }
}
