import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AdminItem1 } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';

@Component({
  selector: 'app-admin-item-name-cell',
  template: `
    <div class="grid min-w-0 gap-1">
      <span class="truncate font-semibold text-on-surface">
        {{ item().effective.name || 'Unnamed item' }}
      </span>
      @for (recipe of item().effective.recipes ?? []; track recipe.recipeId) {
        <span class="min-w-0 text-xs leading-snug text-outline">
          <span class="text-on-surface/80">{{ recipe.name }}</span>
          <span> · {{ recipe.professionName }}</span>
        </span>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminItemNameCellComponent {
  private readonly ctx = injectFlexRenderContext<CellContext<AdminItem1, unknown>>();

  protected item(): AdminItem1 {
    return this.ctx.row.original;
  }
}
