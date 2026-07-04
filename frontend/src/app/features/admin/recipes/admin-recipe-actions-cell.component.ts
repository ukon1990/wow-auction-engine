import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AdminRecipe1 } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { IconButtonComponent } from '@ui';

type AdminRecipeActionsMeta = {
  readonly onEdit?: (recipe: AdminRecipe1) => void;
  readonly onCompare?: (recipe: AdminRecipe1) => void;
  readonly onDeleteOverride?: (recipe: AdminRecipe1) => void;
};

@Component({
  selector: 'app-admin-recipe-actions-cell',
  imports: [IconButtonComponent],
  template: `
    <div class="flex items-center justify-end gap-1">
      <ee-icon-button
        icon="edit"
        label="Edit recipe override"
        tooltip="Open the recipe override editor."
        (pressed)="edit()"
      />
      <ee-icon-button
        icon="import_export"
        label="Compare Blizzard API"
        tooltip="Compare the local recipe data with the current Blizzard API response."
        (pressed)="compare()"
      />
      <ee-icon-button
        icon="delete"
        label="Delete override"
        tooltip="Remove this recipe override and inherit base recipe data again."
        [disabled]="!recipe().hasOverride"
        (pressed)="deleteOverride()"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminRecipeActionsCellComponent {
  private readonly ctx = injectFlexRenderContext<CellContext<AdminRecipe1, unknown>>();

  protected recipe(): AdminRecipe1 {
    return this.ctx.row.original;
  }

  protected edit(): void {
    this.actions()?.onEdit?.(this.recipe());
  }

  protected compare(): void {
    this.actions()?.onCompare?.(this.recipe());
  }

  protected deleteOverride(): void {
    this.actions()?.onDeleteOverride?.(this.recipe());
  }

  private actions(): AdminRecipeActionsMeta | undefined {
    return this.ctx.column.columnDef.meta as AdminRecipeActionsMeta | undefined;
  }
}
