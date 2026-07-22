import { ChangeDetectionStrategy, Component } from '@angular/core';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { ItemLinkComponent } from '@shared/item-link/item-link.component';
import { MarketItemRow } from '@ui';

@Component({
  selector: 'app-market-item-cell',
  imports: [ItemLinkComponent],
  template: `
    <app-item-link
      [itemId]="itemId()"
      [name]="row().name"
      [iconUrl]="row().iconUrl"
      [quality]="row().quality"
      [bonusKey]="row().listingKey?.bonusKey ?? ''"
      [modifierKey]="row().listingKey?.modifierKey ?? ''"
      [petSpeciesId]="row().listingKey?.petSpeciesId ?? 0"
      [scope]="row().preferredScope"
      [buyout]="row().minBuyout"
      [showIcon]="true"
    >
      @if (row().recipeRank; as rank) {
        <span class="ee-label text-outline">{{ rankLabel(rank) }}</span>
      }
    </app-item-link>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketItemCellComponent {
  protected readonly ctx = injectFlexRenderContext<CellContext<MarketItemRow, unknown>>();

  protected row(): MarketItemRow {
    return this.ctx.row.original;
  }

  protected itemId(): number {
    const n = Number.parseInt(this.row().id, 10);
    return Number.isFinite(n) ? n : 0;
  }

  protected rankLabel(rank: number): string {
    return $localize`:@@admin.recipes.form.rankNumber:Rank ${rank}:INTERPOLATION:`;
  }
}
