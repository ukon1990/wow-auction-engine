import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { AuctionHouse } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { SymbolIconComponent } from '@ui';
import type { AuctionHouseActions } from './auction-house-table.columns';

@Component({
  selector: 'app-auction-house-actions-cell',
  imports: [CdkMenu, CdkMenuItem, CdkMenuTrigger, SymbolIconComponent],
  template: `
    <button
      type="button"
      class="inline-grid h-8 w-8 place-items-center rounded-md border border-white/10 text-on-surface-variant transition hover:bg-surface-container-high hover:text-on-surface"
      [cdkMenuTriggerFor]="menu"
      [attr.aria-label]="menuLabel"
    >
      <ee-symbol-icon class="text-[18px]" name="more_horiz" />
    </button>
    <ng-template #menu>
      <div
        cdkMenu
        class="min-w-52 rounded-lg border border-white/10 bg-surface-container-high p-1 text-sm text-on-surface shadow-[0_12px_32px_rgba(0,0,0,0.55)]"
      >
        <button
          cdkMenuItem
          type="button"
          class="block w-full rounded-md px-3 py-2 text-left hover:bg-white/10"
          (cdkMenuItemTriggered)="actions().onDetails(house())"
          i18n="@@admin.auction-house.viewEdit"
        >
          View and edit
        </button>
        <button
          cdkMenuItem
          type="button"
          class="block w-full rounded-md px-3 py-2 text-left hover:bg-white/10 disabled:opacity-40"
          [disabled]="!house().autoUpdate || actions().isSaving(house())"
          (cdkMenuItemTriggered)="actions().onUpdateNow(house())"
          i18n="@@admin.auction-house.updateNow"
        >
          Update now
        </button>
        <button
          cdkMenuItem
          type="button"
          class="block w-full rounded-md px-3 py-2 text-left hover:bg-white/10 disabled:opacity-40"
          [disabled]="actions().isSaving(house())"
          (cdkMenuItemTriggered)="actions().onToggleAutoUpdate(house())"
        >
          {{ house().autoUpdate ? disableLabel : enableLabel }}
        </button>
      </div>
    </ng-template>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuctionHouseActionsCellComponent {
  private readonly ctx = injectFlexRenderContext<CellContext<AuctionHouse, unknown>>();
  protected readonly menuLabel = $localize`:@@admin.auction-house.openActions:Open auction house actions`;
  protected readonly enableLabel = $localize`:@@admin.auction-house.enableAutoUpdate:Enable auto update`;
  protected readonly disableLabel = $localize`:@@admin.auction-house.disableAutoUpdate:Disable auto update`;
  protected house(): AuctionHouse {
    return this.ctx.row.original;
  }
  protected actions(): AuctionHouseActions {
    return this.ctx.column.columnDef.meta as AuctionHouseActions;
  }
}
