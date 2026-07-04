import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AdminItem1 } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { BadgeComponent } from '@ui';

@Component({
  selector: 'app-admin-item-state-cell',
  imports: [BadgeComponent],
  template: `
    <div class="flex flex-wrap gap-2">
      @if (item().hasBase) {
        <ee-badge quality="common">Base</ee-badge>
      }
      @if (item().hasOverride) {
        <ee-badge quality="rare">Override</ee-badge>
      }
      @if (!item().hasBase && item().hasOverride) {
        <ee-badge quality="epic">Manual</ee-badge>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminItemStateCellComponent {
  private readonly ctx = injectFlexRenderContext<CellContext<AdminItem1, unknown>>();

  protected item(): AdminItem1 {
    return this.ctx.row.original;
  }
}
