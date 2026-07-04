import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AdminItem1 } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { IconButtonComponent } from '@ui';

type AdminItemActionsMeta = {
  readonly onEdit?: (item: AdminItem1) => void;
  readonly onCompare?: (item: AdminItem1) => void;
  readonly onDeleteOverride?: (item: AdminItem1) => void;
};

@Component({
  selector: 'app-admin-item-actions-cell',
  imports: [IconButtonComponent],
  template: `
    <div class="flex items-center justify-end gap-1">
      <ee-icon-button
        icon="edit"
        label="Edit item override"
        tooltip="Open the item override editor for this item."
        (pressed)="edit()"
      />
      <ee-icon-button
        icon="import_export"
        label="Compare Blizzard API"
        tooltip="Compare the local item data with the current Blizzard API response."
        (pressed)="compare()"
      />
      <ee-icon-button
        icon="delete"
        label="Delete override"
        tooltip="Remove this item override and inherit base item data again."
        [disabled]="!item().hasOverride"
        (pressed)="deleteOverride()"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminItemActionsCellComponent {
  private readonly ctx = injectFlexRenderContext<CellContext<AdminItem1, unknown>>();

  protected item(): AdminItem1 {
    return this.ctx.row.original;
  }

  protected edit(): void {
    this.actions()?.onEdit?.(this.item());
  }

  protected compare(): void {
    this.actions()?.onCompare?.(this.item());
  }

  protected deleteOverride(): void {
    this.actions()?.onDeleteOverride?.(this.item());
  }

  private actions(): AdminItemActionsMeta | undefined {
    return this.ctx.column.columnDef.meta as AdminItemActionsMeta | undefined;
  }
}
