import { ScrollingModule } from '@angular/cdk/scrolling';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { formatQuality, qualityToneClasses } from '../../helpers/quality';
import { MarketItemRow, TableColumn } from '../../models/ui-models';
import { CurrencyAmountComponent } from '../primitives/currency-amount.component';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';

@Component({
  selector: 'ee-market-table',
  imports: [CurrencyAmountComponent, ScrollingModule, SymbolIconComponent],
  template: `
    <section
      class="ee-glass flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg"
      aria-label="Market items"
    >
      <div
        class="grid grid-cols-[minmax(14rem,3fr)_7rem_minmax(7rem,1.5fr)_minmax(7rem,1.5fr)_minmax(7rem,1.5fr)_6rem] gap-4 border-b border-white/10 bg-surface-container-high px-6 py-4 ee-label text-outline"
        role="row"
      >
        @for (column of columns(); track column.id) {
          <div [class]="columnClass(column.align)" role="columnheader">{{ column.label }}</div>
        }
      </div>
      <div cdkScrollable class="min-h-0 flex-1 overflow-y-auto divide-y divide-white/5">
        @for (row of rows(); track row.id) {
          <button type="button" [class]="rowClass(row)" (click)="rowSelected.emit(row.id)">
            <div class="flex min-w-0 items-center gap-3">
              <div [class]="iconClass(row.quality)">
                @if (row.iconUrl) {
                  <img
                    class="h-6 w-6 rounded-sm object-cover"
                    [src]="row.iconUrl"
                    [alt]="row.name + ' icon'"
                  />
                } @else {
                  <ee-symbol-icon class="text-[18px]" name="deployed_code" />
                }
              </div>
              <span [class]="nameClass(row.quality)">{{ row.name }}</span>
            </div>
            <div [class]="qualityClass(row.quality)">{{ qualityLabel(row.quality) }}</div>
            <ee-currency-amount
              class="justify-self-end"
              [amount]="row.minBuyout"
              [emphasis]="row.selected === true"
            />
            <ee-currency-amount class="justify-self-end opacity-80" [amount]="row.marketValue" />
            <ee-currency-amount
              class="justify-self-end opacity-80"
              [amount]="row.regionalAverage"
            />
            <div class="justify-self-end ee-data text-tertiary-container">
              {{ row.saleRate.toFixed(2) }}
            </div>
          </button>
        } @empty {
          <div class="p-8 text-center text-on-surface-variant">No market items available.</div>
        }
      </div>
      <footer
        class="flex items-center justify-between border-t border-white/10 bg-surface-container-high p-4 ee-data text-outline"
      >
        <span>{{ summary() }}</span>
        <div class="flex gap-2">
          <button
            type="button"
            class="rounded p-1 transition hover:text-primary"
            aria-label="Previous page"
          >
            <ee-symbol-icon name="chevron_left" />
          </button>
          <button
            type="button"
            class="rounded p-1 transition hover:text-primary"
            aria-label="Next page"
          >
            <ee-symbol-icon name="chevron_right" />
          </button>
        </div>
      </footer>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketTableComponent {
  readonly columns = input.required<readonly TableColumn[]>();
  readonly rows = input.required<readonly MarketItemRow[]>();
  readonly summary = input.required<string>();
  readonly rowSelected = output<string>();

  protected columnClass(align: TableColumn['align']): string {
    return align === 'right' ? 'text-right' : 'text-left';
  }

  protected rowClass(row: MarketItemRow): string {
    const base =
      'grid w-full grid-cols-[minmax(14rem,3fr)_7rem_minmax(7rem,1.5fr)_minmax(7rem,1.5fr)_minmax(7rem,1.5fr)_6rem] items-center gap-4 px-6 py-3 text-left transition hover:bg-white/5';
    return row.selected
      ? `${base} border-l-2 border-primary bg-primary/10 shadow-[inset_0_0_20px_rgba(236,185,19,0.05)]`
      : base;
  }

  protected iconClass(quality: MarketItemRow['quality']): string {
    return `flex h-8 w-8 shrink-0 items-center justify-center rounded border bg-surface ${qualityToneClasses(quality)}`;
  }

  protected nameClass(quality: MarketItemRow['quality']): string {
    return `truncate text-sm font-semibold ${qualityToneClasses(quality).split(' ')[0]}`;
  }

  protected qualityClass(quality: MarketItemRow['quality']): string {
    return `ee-label ${qualityToneClasses(quality).split(' ')[0]}`;
  }

  protected qualityLabel(quality: MarketItemRow['quality']): string {
    return formatQuality(quality);
  }
}
