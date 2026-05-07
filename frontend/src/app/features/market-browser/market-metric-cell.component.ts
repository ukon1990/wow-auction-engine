import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';

import { LocaleService } from '@core/services/locale.service';
import { CurrencyAmountComponent, MarketItemRow } from '@ui';

@Component({
  selector: 'app-market-metric-cell',
  imports: [CurrencyAmountComponent, DecimalPipe],
  template: `
    @switch (columnId()) {
      @case ('selectedPrice') {
        <ee-currency-amount
          class="justify-self-end"
          [amount]="row().minBuyout"
          [emphasis]="row().selected === true"
        />
      }
      @case ('selectedQuantity') {
        @if (row().selectedQuantity !== undefined) {
          <div class="justify-self-end ee-data text-on-surface">
            {{ row().selectedQuantity | number: '1.0-0' : selectedLocaleForNumberPipe() }}
          </div>
        } @else {
          <ee-currency-amount class="justify-self-end opacity-80" [amount]="row().marketValue" />
        }
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketMetricCellComponent {
  protected readonly ctx = injectFlexRenderContext<CellContext<MarketItemRow, unknown>>();
  private readonly locale = inject(LocaleService);

  protected row(): MarketItemRow {
    return this.ctx.row.original;
  }

  protected columnId(): string {
    return this.ctx.column.id;
  }

  protected selectedLocaleForNumberPipe(): string | undefined {
    return this.locale.formatLocale();
  }
}
