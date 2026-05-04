import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';

import { WowheadItemTooltipDirective } from '@core/directives/wowhead-item-tooltip';
import { MarketItemRow, qualityToneClasses, SymbolIconComponent } from '@ui';

@Component({
  selector: 'app-market-item-cell',
  imports: [RouterLink, SymbolIconComponent, WowheadItemTooltipDirective],
  template: `
    <a
      [routerLink]="['item', itemId()]"
      [relativeTo]="auctionsRoute"
      [queryParams]="variantQueryParams()"
      [state]="backNavState()"
      class="flex min-w-0 items-center gap-3 rounded no-underline text-inherit outline-none transition hover:bg-white/5 focus-visible:ring-2 focus-visible:ring-primary/60"
    >
      <div [class]="iconClass()">
        @if (row().iconUrl) {
          <img
            class="h-6 w-6 rounded-sm object-cover"
            [src]="row().iconUrl"
            [alt]="row().name + ' icon'"
          />
        } @else {
          <ee-symbol-icon class="text-[18px]" name="deployed_code" />
        }
      </div>
      <span
        appWowheadItemTooltip
        [itemId]="itemId()"
        linkType="item"
        [currentBuyout]="row().minBuyout"
        [class]="nameClass()"
        >{{ row().name }}</span
      >
    </a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketItemCellComponent {
  protected readonly ctx = injectFlexRenderContext<CellContext<MarketItemRow, unknown>>();
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Parent `auctions` route so `item/:id` resolves under the same segment. */
  protected readonly auctionsRoute = this.route.parent!;

  protected row(): MarketItemRow {
    return this.ctx.row.original;
  }

  protected itemId(): number {
    const n = Number.parseInt(this.row().id, 10);
    return Number.isFinite(n) ? n : 0;
  }

  protected variantQueryParams(): Record<string, string | number> | undefined {
    const row = this.row();
    const lk = row.listingKey;
    const scope = row.preferredScope;
    if (!lk && !scope) return undefined;
    return {
      ...(lk
        ? {
            bonusKey: lk.bonusKey,
            modifierKey: lk.modifierKey,
            petSpeciesId: lk.petSpeciesId,
          }
        : {}),
      ...(scope ? { scope } : {}),
    };
  }

  protected backNavState(): { returnUrl: string; returnLabel: string } {
    return { returnUrl: this.router.url, returnLabel: 'Market' };
  }

  protected iconClass(): string {
    return `flex h-8 w-8 shrink-0 items-center justify-center rounded border bg-surface ${qualityToneClasses(this.row().quality)}`;
  }

  protected nameClass(): string {
    return `truncate text-sm font-semibold ${qualityToneClasses(this.row().quality).split(' ')[0]}`;
  }
}
