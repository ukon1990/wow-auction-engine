import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AdminExpansionItemRange } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { IconButtonComponent } from '@ui';

type ExpansionRangeActionsMeta = {
  readonly onEdit?: (range: AdminExpansionItemRange) => void;
  readonly onDelete?: (range: AdminExpansionItemRange) => void;
};

@Component({
  selector: 'app-expansion-range-actions-cell',
  imports: [IconButtonComponent],
  template: `
    <div class="flex items-center justify-end gap-1">
      <ee-icon-button icon="edit" label="Edit range" (pressed)="edit()" />
      <ee-icon-button icon="delete" label="Delete range" (pressed)="delete()" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpansionRangeActionsCellComponent {
  private readonly ctx = injectFlexRenderContext<CellContext<AdminExpansionItemRange, unknown>>();

  protected edit(): void {
    const meta = this.ctx.column.columnDef.meta as ExpansionRangeActionsMeta | undefined;
    meta?.onEdit?.(this.ctx.row.original);
  }

  protected delete(): void {
    const meta = this.ctx.column.columnDef.meta as ExpansionRangeActionsMeta | undefined;
    meta?.onDelete?.(this.ctx.row.original);
  }
}
