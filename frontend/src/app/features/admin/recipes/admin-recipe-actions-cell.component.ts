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
      <ee-icon-button icon="edit" [label]="editLabel" [tooltip]="editTooltip" (pressed)="edit()" />
      <ee-icon-button
        icon="import_export"
        [label]="compareLabel"
        [tooltip]="compareTooltip"
        (pressed)="compare()"
      />
      <ee-icon-button
        icon="delete"
        [label]="deleteLabel"
        [tooltip]="deleteTooltip"
        [disabled]="!recipe().hasOverride"
        (pressed)="deleteOverride()"
      />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminRecipeActionsCellComponent {
  protected readonly editLabel = $localize`:@@admin.recipes.actions.edit:Edit recipe override`;
  protected readonly editTooltip = $localize`:@@admin.recipes.actions.editTooltip:Open the recipe override editor.`;
  protected readonly compareLabel = $localize`:@@admin.recipes.actions.compare:Compare Blizzard API`;
  protected readonly compareTooltip = $localize`:@@admin.recipes.actions.compareTooltip:Compare the local recipe data with the current Blizzard API response.`;
  protected readonly deleteLabel = $localize`:@@admin.recipes.actions.delete:Delete override`;
  protected readonly deleteTooltip = $localize`:@@admin.recipes.actions.deleteTooltip:Remove this recipe override and inherit base recipe data again.`;

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
