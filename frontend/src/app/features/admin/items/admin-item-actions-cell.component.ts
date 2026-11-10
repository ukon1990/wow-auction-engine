import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AdminItem } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { RowAction, RowActionsMenuComponent } from '@ui';

type AdminItemActionsMeta = {
  readonly onEdit?: (item: AdminItem) => void;
  readonly onAssociateRecipe?: (item: AdminItem) => void;
  readonly onCompare?: (item: AdminItem) => void;
  readonly onDeleteOverride?: (item: AdminItem) => void;
};

@Component({
  selector: 'app-admin-item-actions-cell',
  imports: [RowActionsMenuComponent],
  template: `
    <ee-row-actions-menu
      primaryLabel="Edit"
      [primaryAriaLabel]="'Edit override for ' + (item().effective.name ?? item().id)"
      [actions]="menuActions()"
      menuLabel="More actions"
      (primary)="edit()"
      (actionSelected)="onAction($event)"
    />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminItemActionsCellComponent {
  private readonly ctx = injectFlexRenderContext<CellContext<AdminItem, unknown>>();

  protected item(): AdminItem {
    return this.ctx.row.original;
  }

  protected menuActions(): RowAction[] {
    return [
      { id: 'compare', label: 'Compare with Blizzard API' },
      { id: 'recipe', label: 'Link recipe' },
      {
        id: 'delete',
        label: 'Delete override',
        danger: true,
        disabled: !this.item().hasOverride,
      },
    ];
  }

  protected onAction(id: string): void {
    if (id === 'compare') this.compare();
    else if (id === 'recipe') this.associateRecipe();
    else if (id === 'delete') this.deleteOverride();
  }

  protected edit(): void {
    this.actions()?.onEdit?.(this.item());
  }

  protected associateRecipe(): void {
    this.actions()?.onAssociateRecipe?.(this.item());
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
