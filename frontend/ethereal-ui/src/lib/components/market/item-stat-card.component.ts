import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { CurrencyAmount } from '../../models/ui-models';
import { CurrencyAmountComponent } from '../primitives/currency-amount.component';
import { SymbolIconComponent, SymbolIconName } from '../primitives/symbol-icon.component';

export type StatCardTone = 'default' | 'primary' | 'good' | 'bad';

@Component({
  selector: 'ee-item-stat-card',
  imports: [CurrencyAmountComponent, SymbolIconComponent],
  template: `
    <section [class]="cardClass()">
      <span class="ee-label flex items-center gap-2 text-outline">
        <ee-symbol-icon class="text-base" [name]="icon()" />
        {{ label() }}
      </span>
      <div class="mt-3 flex items-baseline gap-2">
        @if (currency()) {
          <ee-currency-amount [amount]="currency()!" [emphasis]="tone() === 'primary'" />
        } @else {
          <span class="font-space-mono text-3xl font-bold text-on-surface">{{ value() }}</span>
          @if (unit()) {
            <span class="ee-data text-outline">{{ unit() }}</span>
          }
        }
      </div>
      @if (caption()) {
        <p [class]="captionClass()">{{ caption() }}</p>
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemStatCardComponent {
  readonly label = input.required<string>();
  readonly icon = input<SymbolIconName | string>('query_stats');
  readonly value = input<string | number>('');
  readonly unit = input('');
  readonly caption = input('');
  readonly currency = input<CurrencyAmount | undefined>();
  readonly tone = input<StatCardTone>('default');

  protected cardClass(): string {
    const emphasis =
      this.tone() === 'primary' ? 'border-primary/30 shadow-[0_0_10px_rgba(236,185,19,0.1)]' : '';
    return `ee-glass rounded-lg p-inner-padding ${emphasis}`;
  }

  protected captionClass(): string {
    const toneClasses: Record<StatCardTone, string> = {
      default: 'text-on-surface-variant',
      primary: 'text-primary',
      good: 'text-tertiary-container',
      bad: 'text-error',
    };
    return `mt-2 ee-data ${toneClasses[this.tone()]}`;
  }
}
