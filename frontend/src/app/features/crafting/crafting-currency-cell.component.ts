import { ChangeDetectionStrategy, Component } from '@angular/core';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';

import { copperToCurrencyAmount, CurrencyAmountComponent } from '@ui';

import type { CraftingTableRow } from './crafting-browser.models';

@Component({
  selector: 'app-crafting-currency-cell',
  imports: [CurrencyAmountComponent],
  template: `
    @if (copper() != null) {
      <ee-currency-amount class="justify-self-end" [amount]="amount()" />
    } @else {
      <span class="justify-self-end text-outline">—</span>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CraftingCurrencyCellComponent {
  protected readonly ctx = injectFlexRenderContext<CellContext<CraftingTableRow, unknown>>();

  protected row(): CraftingTableRow {
    return this.ctx.row.original;
  }

  protected columnId(): string {
    return this.ctx.column.id;
  }

  protected copper(): number | null {
    const r = this.row();
    switch (this.columnId()) {
      case 'outputPrice':
        return r.outputPriceCopper;
      case 'reagentCost':
        return r.reagentCostCopper;
      case 'profit':
        return r.profitCopper;
      default:
        return null;
    }
  }

  protected amount() {
    return copperToCurrencyAmount(this.copper() ?? 0);
  }
}
