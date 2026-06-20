import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';

import { LocaleService } from '@core/services/locale.service';
import { copperToCurrencyAmount, CurrencyAmountComponent, MarketItemRow } from '@ui';

@Component({
  selector: 'app-market-metric-cell',
  imports: [CurrencyAmountComponent, DecimalPipe],
  template: `
    @switch (columnId()) {
      @case ('selectedPrice') {
        <div class="space-y-1">
          <ee-currency-amount [amount]="row().minBuyout" [emphasis]="row().selected === true" />
          @if (hasPercentileRange()) {
            <div
              class="inline-flex flex-wrap items-center gap-x-1 text-[11px] leading-none text-outline"
            >
              <span>p25</span>
              <ee-currency-amount [amount]="p25Amount()" />
              <span>/ p75</span>
              <ee-currency-amount [amount]="p75Amount()" />
            </div>
          }
        </div>
      }
      @case ('selectedQuantity') {
        @if (row().selectedQuantity !== undefined) {
          <div class="ee-data text-on-surface">
            {{ row().selectedQuantity | number: '1.0-0' : selectedLocaleForNumberPipe() }}
          </div>
        } @else {
          <ee-currency-amount class="opacity-80" [amount]="row().marketValue" />
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

  protected hasPercentileRange(): boolean {
    const row = this.row();
    return row.p25PriceCopper !== undefined && row.p75PriceCopper !== undefined;
  }

  protected p25Amount() {
    return copperToCurrencyAmount(this.row().p25PriceCopper ?? 0);
  }

  protected p75Amount() {
    return copperToCurrencyAmount(this.row().p75PriceCopper ?? 0);
  }
}
