import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import type { CurrencyAmount } from '../../models/ui-models';
import { TooltipCardComponent, type TooltipRow } from '../primitives/tooltip-card.component';

@Component({
  selector: 'ee-item-tooltip-card',
  imports: [TooltipCardComponent],
  template: ` <ee-tooltip-card [title]="name()" [subtitle]="subtitle()" [rows]="tooltipRows()" /> `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemTooltipCardComponent {
  readonly name = input.required<string>();
  readonly subtitle = input('');
  readonly lines = input<readonly string[]>([]);
  readonly flavor = input('');
  readonly sellPrice = input.required<CurrencyAmount>();

  protected tooltipRows(): readonly TooltipRow[] {
    return [
      ...this.lines().map((line): TooltipRow => ({ label: line, value: '' })),
      ...(this.flavor() ? [{ label: this.flavor(), value: '' }] : []),
      {
        label: 'Sell Price',
        amount: this.sellPrice(),
      },
    ];
  }
}
