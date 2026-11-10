import { DecimalPipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';

import { LocaleService } from '@core/services/locale.service';
import { CurrencyAmountComponent, MarketItemRow } from '@ui';

@Component({
  selector: 'app-market-metric-cell',
  imports: [CurrencyAmountComponent, DecimalPipe, PercentPipe],
  template: `
    @switch (columnId()) {
      @case ('selectedPrice') {
        <div class="min-w-0 max-w-full lg:flex lg:justify-end">
          <ee-currency-amount [amount]="row().minBuyout" [emphasis]="row().selected === true" />
        </div>
      }
      @case ('selectedQuantity') {
        @if (row().selectedQuantity !== undefined) {
          <div class="ee-data text-on-surface lg:ml-auto">
            {{ row().selectedQuantity | number: '1.0-0' : selectedLocaleForNumberPipe() }}
          </div>
        } @else {
          <ee-currency-amount class="opacity-80 lg:ml-auto" [amount]="row().marketValue" />
        }
      }
      @case ('saleRate') {
        @if (row().saleRate != null) {
          <div class="ee-data text-on-surface lg:ml-auto">
            {{ row().saleRate | percent: '1.0-1' : selectedLocaleForNumberPipe() }}
          </div>
        } @else {
          <span class="text-outline lg:ml-auto">—</span>
        }
      }
      @case ('soldPerDay') {
        @if (row().soldPerDay != null) {
          <div class="ee-data text-on-surface lg:ml-auto">
            {{ row().soldPerDay | number: '1.0-2' : selectedLocaleForNumberPipe() }}
          </div>
        } @else {
          <span class="text-outline lg:ml-auto">—</span>
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
