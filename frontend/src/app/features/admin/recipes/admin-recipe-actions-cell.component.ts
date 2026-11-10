import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AdminRecipe } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { RowAction, RowActionsMenuComponent } from '@ui';

type AdminRecipeActionsMeta = {
  readonly onEdit?: (recipe: AdminRecipe) => void;
  readonly onCompare?: (recipe: AdminRecipe) => void;
  readonly onDeleteOverride?: (recipe: AdminRecipe) => void;
};

@Component({
  selector: 'app-admin-recipe-actions-cell',
  imports: [RowActionsMenuComponent],
  template: `
    <ee-row-actions-menu
      [primaryLabel]="editButtonLabel"
      [primaryAriaLabel]="editLabel"
      [actions]="menuActions()"
      (primary)="edit()"
      (actionSelected)="onAction($event)"
    />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminRecipeActionsCellComponent {
  protected readonly editButtonLabel = $localize`:@@admin.recipes.actions.editButton:Edit`;
  protected readonly editLabel = $localize`:@@admin.recipes.actions.edit:Edit recipe override`;
  protected readonly deleteLabel = $localize`:@@admin.recipes.actions.delete:Delete override`;

  private readonly ctx = injectFlexRenderContext<CellContext<AdminRecipe, unknown>>();

  protected recipe(): AdminRecipe {
    return this.ctx.row.original;
  }

  protected menuActions(): RowAction[] {
    return [
      {
        id: 'compare',
        label: $localize`:@@admin.recipes.actions.compareMenu:Compare with Blizzard API`,
      },
      { id: 'delete', label: this.deleteLabel, danger: true, disabled: !this.recipe().hasOverride },
    ];
  }

  protected onAction(id: string): void {
    if (id === 'compare') this.compare();
    else if (id === 'delete') this.deleteOverride();
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
