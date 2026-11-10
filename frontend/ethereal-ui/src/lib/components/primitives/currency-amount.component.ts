import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { formatCurrencyAmount, formatCurrencyPart, hasCurrencyValue } from '../../helpers/currency';
import { CurrencyAmount } from '../../models/ui-models';

@Component({
  selector: 'ee-currency-amount',
  template: `
    <span [class]="hostClass()" [attr.aria-label]="ariaLabel()">
      @if (hasValue()) {
        @if (amount().negative) {
          <span class="-mr-1" aria-hidden="true">−</span>
        } @else if (signed()) {
          <span class="-mr-1" aria-hidden="true">+</span>
        }
        @if (amount().gold) {
          <span>{{ format(amount().gold) }}</span>
          <span [class]="coinClass()" aria-hidden="true"></span>
        }
        @if (amount().silver) {
          <span>{{ format(amount().silver) }}</span>
          <span [class]="coinClass('silver')" aria-hidden="true"></span>
        }
        @if (amount().copper) {
          <span>{{ format(amount().copper) }}</span>
          <span [class]="coinClass('copper')" aria-hidden="true"></span>
        }
      } @else {
        <span>---</span>
      }
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CurrencyAmountComponent {
  readonly amount = input.required<CurrencyAmount>();
  readonly emphasis = input(false);
  /** Profit style: a + or − sign, green when positive and red when negative. */
  readonly signed = input(false);

  protected hasValue(): boolean {
    return hasCurrencyValue(this.amount());
  }

  protected hostClass(): string {
    if (this.signed() && this.hasValue()) {
      const tone = this.amount().negative ? 'text-error' : 'text-status-ok';
      return `ee-data inline-flex items-center justify-end gap-1 font-bold ${tone}`;
    }
    return this.emphasis()
      ? 'ee-data inline-flex items-center justify-end gap-1 font-bold text-primary'
      : 'ee-data inline-flex items-center justify-end gap-1 text-on-surface';
  }

  protected coinClass(kind: 'gold' | 'silver' | 'copper' = 'gold'): string {
    return `ee-coin ee-coin-${kind} h-3 w-3`;
  }

  protected format(value: number | undefined): string {
    return formatCurrencyPart(value);
  }

  protected ariaLabel(): string {
    return formatCurrencyAmount(this.amount());
  }
}
