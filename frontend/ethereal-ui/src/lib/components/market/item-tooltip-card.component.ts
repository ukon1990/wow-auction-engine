import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { CurrencyAmount } from '../../models/ui-models';
import { CurrencyAmountComponent } from '../primitives/currency-amount.component';

@Component({
  selector: 'ee-item-tooltip-card',
  imports: [CurrencyAmountComponent],
  template: `
    <article class="ee-glass relative overflow-hidden rounded-lg p-inner-padding">
      <div
        class="pointer-events-none absolute -left-10 -top-10 h-32 w-32 rounded-full bg-primary/10 blur-3xl"
      ></div>
      <h2 class="font-cinzel text-xl font-bold text-primary">{{ name() }}</h2>
      <p class="mt-1 ee-data text-secondary">{{ subtitle() }}</p>
      <div class="mt-4 space-y-2 text-sm text-on-surface">
        @for (line of lines(); track line) {
          <p>{{ line }}</p>
        }
      </div>
      @if (flavor()) {
        <p class="mt-5 text-sm italic text-primary">{{ flavor() }}</p>
      }
      <div class="mt-5 flex items-center justify-between border-t border-white/5 pt-3">
        <span class="ee-label text-outline">Sell Price</span>
        <ee-currency-amount [amount]="sellPrice()" />
      </div>
    </article>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemTooltipCardComponent {
  readonly name = input.required<string>();
  readonly subtitle = input('');
  readonly lines = input<readonly string[]>([]);
  readonly flavor = input('');
  readonly sellPrice = input.required<CurrencyAmount>();
}
