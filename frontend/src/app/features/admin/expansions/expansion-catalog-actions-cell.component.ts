import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AdminExpansion } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { IconButtonComponent } from '@ui';

type ExpansionCatalogActionsMeta = {
  readonly onEdit?: (expansion: AdminExpansion) => void;
  readonly onDelete?: (expansion: AdminExpansion) => void;
};

@Component({
  selector: 'app-expansion-catalog-actions-cell',
  imports: [IconButtonComponent],
  template: `
    <div class="flex items-center justify-end gap-1">
      <ee-icon-button icon="edit" label="Edit expansion" (pressed)="edit()" />
      <ee-icon-button icon="delete" label="Delete expansion" (pressed)="delete()" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpansionCatalogActionsCellComponent {
  private readonly ctx = injectFlexRenderContext<CellContext<AdminExpansion, unknown>>();

  protected edit(): void {
    const meta = this.ctx.column.columnDef.meta as ExpansionCatalogActionsMeta | undefined;
    meta?.onEdit?.(this.ctx.row.original);
  }

  protected delete(): void {
    const meta = this.ctx.column.columnDef.meta as ExpansionCatalogActionsMeta | undefined;
    meta?.onDelete?.(this.ctx.row.original);
  }
}
