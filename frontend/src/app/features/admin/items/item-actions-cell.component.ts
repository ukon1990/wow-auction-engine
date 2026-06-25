import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AdminItem } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';

type ItemActionsMeta = {
  readonly onEdit?: (item: AdminItem) => void;
  readonly onRemoveOverride?: (item: AdminItem) => void;
};

@Component({
  selector: 'app-item-actions-cell',
  template: `
    <div class="flex justify-end gap-2">
      <button
        type="button"
        class="h-8 rounded-md border border-white/10 px-3 text-sm font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
        (click)="edit()"
      >
        Edit
      </button>
      @if (ctx.row.original.hasOverride) {
        <button
          type="button"
          class="h-8 rounded-md border border-error/30 px-3 text-sm font-semibold text-error transition hover:bg-error/10 focus:outline-none focus:ring-2 focus:ring-error"
          (click)="removeOverride()"
        >
          Clear override
        </button>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemActionsCellComponent {
  protected readonly ctx = injectFlexRenderContext<CellContext<AdminItem, unknown>>();

  protected edit(): void {
    const meta = this.ctx.column.columnDef.meta as ItemActionsMeta | undefined;
    meta?.onEdit?.(this.ctx.row.original);
  }

  protected removeOverride(): void {
    const meta = this.ctx.column.columnDef.meta as ItemActionsMeta | undefined;
    meta?.onRemoveOverride?.(this.ctx.row.original);
  }
}
