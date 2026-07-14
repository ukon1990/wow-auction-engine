import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';

import { WowheadItemTooltipDirective } from '@core/directives/wowhead-item-tooltip';
import { copperToCurrencyAmount, qualityToneClasses, SymbolIconComponent } from '@ui';

import type { CraftingTableRow } from './crafting-browser.models';
import { profileFitSummary } from './crafting-profile-fit';

@Component({
  selector: 'app-crafting-item-cell',
  imports: [RouterLink, SymbolIconComponent, WowheadItemTooltipDirective],
  template: `
    <a
      [routerLink]="recipeAndItemPath()"
      [queryParams]="variantQueryParams()"
      [state]="backNavState()"
      class="flex min-w-0 flex-col gap-0.5 rounded no-underline text-inherit outline-none transition hover:bg-white/5 focus-visible:ring-2 focus-visible:ring-primary/60"
    >
      <div class="flex min-w-0 items-center gap-3">
        <div [class]="iconClass()">
          @if (row().iconUrl) {
            <img
              class="h-6 w-6 rounded-sm object-cover"
              [src]="row().iconUrl"
              [alt]="row().craftedItemName + ' icon'"
            />
          } @else {
            <ee-symbol-icon class="text-[18px]" name="deployed_code" />
          }
        </div>
        <span
          appWowheadItemTooltip
          [itemId]="itemId()"
          linkType="item"
          [bonusKey]="row().listingKey.bonusKey"
          [currentBuyout]="buyoutAmount()"
          [class]="nameClass()"
          >{{ row().craftedItemName }}</span
        >
      </div>
      <span class="truncate pl-11 text-xs text-outline">{{ row().variantSummary }}</span>
      @if (row().profileFit; as profileFit) {
        <span class="pl-11 text-xs text-primary-container">{{ profileFitText(profileFit) }}</span>
      }
    </a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CraftingItemCellComponent {
  protected readonly ctx = injectFlexRenderContext<CellContext<CraftingTableRow, unknown>>();
  private readonly router = inject(Router);

  /**
   * Build an absolute crafting-shell-relative path that does not rely on `this.route.parent`.
   * The cell may be rendered outside a normal route context (Storybook, isolated component tests),
   * in which case a `route.parent!` non-null assertion would throw at runtime. Reconstructing the
   * path from the current URL is robust to those cases and still navigates to the correct
   * `<region>/<realm>/crafting/<recipeId>/<itemId>` target.
   */
  protected recipeAndItemPath(): unknown[] {
    const segments = this.router.url.split('?')[0].split('/').filter(Boolean);
    const craftingIdx = segments.indexOf('crafting');
    const base = craftingIdx >= 0 ? segments.slice(0, craftingIdx + 1) : segments;
    return ['/', ...base, this.recipeId(), this.itemId()];
  }

  protected row(): CraftingTableRow {
    return this.ctx.row.original;
  }

  protected itemId(): number {
    return this.row().craftedItemId;
  }

  protected recipeId(): number {
    return this.row().recipeId;
  }

  protected variantQueryParams(): Record<string, string | number> {
    const lk = this.row().listingKey;
    return {
      bonusKey: lk.bonusKey,
      modifierKey: lk.modifierKey,
      petSpeciesId: lk.petSpeciesId,
    };
  }

  protected buyoutAmount() {
    const c = this.row().minBuyoutCopper;
    return c != null ? copperToCurrencyAmount(c) : null;
  }

  protected backNavState(): { returnUrl: string; returnLabel: string } {
    return { returnUrl: this.router.url, returnLabel: 'Crafting' };
  }

  protected iconClass(): string {
    return `flex h-8 w-8 shrink-0 items-center justify-center rounded border bg-surface ${qualityToneClasses(this.row().quality)}`;
  }

  protected nameClass(): string {
    return `truncate text-sm font-semibold ${qualityToneClasses(this.row().quality).split(' ')[0]}`;
  }

  protected profileFitText = profileFitSummary;
}
