import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import type { CurrencyAmount } from '../../models/ui-models';
import { CurrencyAmountComponent } from './currency-amount.component';

export interface TooltipRow {
  readonly label: string;
  readonly value?: string | number | null;
  readonly amount?: CurrencyAmount | null;
}

@Component({
  selector: 'ee-tooltip-card',
  imports: [CurrencyAmountComponent],
  template: `
    <article
      class="ee-glass relative min-w-56 overflow-hidden rounded-lg text-left text-on-surface"
      [class.p-3]="compact()"
      [class.p-inner-padding]="!compact()"
      role="tooltip"
    >
      <div
        class="pointer-events-none absolute -left-10 -top-10 h-32 w-32 rounded-full bg-primary/10 blur-3xl"
      ></div>
      <h2
        class="font-cinzel font-bold text-primary"
        [class.text-lg]="compact()"
        [class.text-xl]="!compact()"
      >
        {{ title() }}
      </h2>
      @if (subtitle()) {
        <p class="mt-1 ee-data text-secondary">{{ subtitle() }}</p>
      }

      @let bodyRows = contentRows();
      @if (bodyRows.length) {
        <div class="mt-4 space-y-2 text-sm text-on-surface" [class.mt-3]="compact()">
          @for (row of bodyRows; track row.label) {
            @if (hasInlineValue(row)) {
              <div class="flex items-baseline justify-between gap-4">
                <span class="text-outline">{{ row.label }}</span>
                <span class="font-space-mono text-on-surface">{{ displayValue(row.value) }}</span>
              </div>
            } @else {
              <p>{{ row.label }}</p>
            }
          }
        </div>
      } @else if (emptyMessage()) {
        <p class="mt-4 text-sm text-outline">{{ emptyMessage() }}</p>
      }

      @let footerRows = amountRows();
      @if (footerRows.length) {
        <div class="mt-5 space-y-2 border-t border-white/5 pt-3">
          @for (row of footerRows; track row.label) {
            <div class="flex items-center justify-between gap-4">
              <span class="ee-label text-outline">{{ row.label }}</span>
              <ee-currency-amount [amount]="row.amount!" />
            </div>
          }
        </div>
      }
    </article>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TooltipCardComponent {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly rows = input<readonly TooltipRow[]>([]);
  readonly emptyMessage = input('');
  readonly compact = input(false);

  protected contentRows(): readonly TooltipRow[] {
    return this.rows().filter((r) => !r.amount);
  }

  protected amountRows(): readonly TooltipRow[] {
    return this.rows().filter((r) => !!r.amount);
  }

  protected hasInlineValue(row: TooltipRow): boolean {
    return row.value !== null && row.value !== undefined && row.value !== '';
  }

  protected displayValue(value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    return String(value);
  }
}
