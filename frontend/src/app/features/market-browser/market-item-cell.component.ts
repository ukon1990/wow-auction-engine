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
      @if (details(); as text) {
        <span class="block truncate text-xs text-outline">{{ text }}</span>
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
    const itemId = Number.parseInt(this.row().id, 10);
    return Number.isFinite(itemId) ? itemId : 0;
  }

  /** Class · subclass · rank, shown under the item name. */
  protected details(): string {
    const row = this.row();
    return [
      row.itemClassName,
      row.itemSubclassName,
      row.recipeRank ? this.rankLabel(row.recipeRank) : undefined,
    ]
      .filter((part): part is string => Boolean(part?.trim()))
      .join(' · ');
  }

  protected rankLabel(rank: number): string {
    return $localize`:@@admin.recipes.form.rankNumber:Rank ${rank}:INTERPOLATION:`;
  }
}
