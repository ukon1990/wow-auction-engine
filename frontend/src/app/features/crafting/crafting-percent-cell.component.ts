import { DecimalPipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';

import { RealmSelectionService } from '@core/services/realm-selection.service';

import type { CraftingTableRow } from './crafting-browser.models';

@Component({
  selector: 'app-crafting-percent-cell',
  imports: [DecimalPipe, PercentPipe],
  template: `
    @switch (columnId()) {
      @case ('saleRate') {
        @if (value() != null) {
          <div class="ee-data text-on-surface">
            {{ value()! | percent: '1.0-1' : selectedLocaleForNumberPipe() }}
          </div>
        } @else {
          <span class="text-outline">—</span>
        }
      }
      @case ('soldPerDay') {
        @if (value() != null) {
          <div class="ee-data text-on-surface">
            {{ value()! | number: '1.0-2' : selectedLocaleForNumberPipe() }}
          </div>
        } @else {
          <span class="text-outline">—</span>
        }
      }
      @default {
        @if (value() != null) {
          <div class="ee-data text-on-surface">
            {{ value()! | number: '1.1-1' : selectedLocaleForNumberPipe() }}%
          </div>
        } @else {
          <span class="text-outline">—</span>
        }
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CraftingPercentCellComponent {
  protected readonly ctx = injectFlexRenderContext<CellContext<CraftingTableRow, unknown>>();
  private readonly realmSelection = inject(RealmSelectionService);

  protected columnId(): string {
    return this.ctx.column.id;
  }

  protected value(): number | null {
    const r = this.ctx.row.original;
    switch (this.columnId()) {
      case 'roiPercent':
        return r.roiPercent;
      case 'outputPriceChangePercent':
        return r.outputPriceChangePercent;
      case 'saleRate':
        return r.saleRate;
      case 'soldPerDay':
        return r.soldPerDay;
      default:
        return null;
    }
  }

  protected selectedLocaleForNumberPipe(): string | undefined {
    return this.realmSelection.selected()?.locale?.replace('_', '-');
  }
}
