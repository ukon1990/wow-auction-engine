import { ChangeDetectionStrategy, Component } from '@angular/core';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { ItemLinkComponent } from '@shared/item-link/item-link.component';
import type { CraftingTableRow } from './crafting-browser.models';
import { profileFitSummary } from './crafting-profile-fit';

@Component({
  selector: 'app-crafting-item-cell',
  imports: [ItemLinkComponent],
  template: `
    <app-item-link
      [itemId]="itemId()"
      [name]="row().craftedItemName"
      [iconUrl]="row().iconUrl"
      [quality]="row().quality"
      [bonusKey]="row().listingKey.bonusKey"
      [modifierKey]="row().listingKey.modifierKey"
      [petSpeciesId]="row().listingKey.petSpeciesId"
      [recipeId]="recipeId()"
      [buyoutCopper]="row().minBuyoutCopper"
      [showIcon]="true"
    >
      @if (recipeSubtext(); as recipeSubtext) {
        <span class="block truncate text-xs text-outline">{{ recipeSubtext }}</span>
      }
      @if (row().variantSummary) {
        <span class="block truncate text-xs text-outline">{{ row().variantSummary }}</span>
      }
      @if (row().profileFit; as profileFit) {
        <span class="block truncate text-xs text-primary">{{ profileFitText(profileFit) }}</span>
      }
    </app-item-link>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CraftingItemCellComponent {
  protected readonly ctx = injectFlexRenderContext<CellContext<CraftingTableRow, unknown>>();

  protected row(): CraftingTableRow {
    return this.ctx.row.original;
  }

  protected itemId(): number {
    return this.row().craftedItemId;
  }

  protected recipeId(): number {
    return this.row().recipeId;
  }

  protected profileFitText = profileFitSummary;

  protected recipeSubtext(): string | null {
    const row = this.row();
    const itemName = row.craftedItemName.trim();
    const recipeName = row.recipeName.trim();
    const rank = row.recipeRank;
    const rankLabel =
      rank == null ? null : $localize`:@@admin.recipes.form.rankNumber:Rank ${rank}:INTERPOLATION:`;

    const parts = [
      row.professionName?.trim() || null,
      recipeName && recipeName !== itemName ? recipeName : null,
      rankLabel,
    ].filter((part): part is string => Boolean(part));
    return parts.length ? parts.join(' · ') : null;
  }
}
