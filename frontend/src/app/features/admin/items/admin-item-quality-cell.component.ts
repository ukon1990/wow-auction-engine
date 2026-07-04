import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AdminItem1 } from '@api/generated';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { ItemQuality, QualityBadgeComponent } from '@ui';

const supportedQualities = new Set<ItemQuality>([
  'common',
  'uncommon',
  'rare',
  'epic',
  'legendary',
]);

@Component({
  selector: 'app-admin-item-quality-cell',
  imports: [QualityBadgeComponent],
  template: `
    @if (quality(); as qualityValue) {
      <ee-quality-badge [quality]="qualityValue" />
    } @else {
      <span class="text-outline">—</span>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminItemQualityCellComponent {
  private readonly ctx = injectFlexRenderContext<CellContext<AdminItem1, unknown>>();

  protected quality(): ItemQuality | null {
    const raw =
      this.ctx.row.original.effective.quality?.type ??
      this.ctx.row.original.effective.quality?.name ??
      '';
    const normalized = raw.toLowerCase();
    return supportedQualities.has(normalized as ItemQuality) ? (normalized as ItemQuality) : null;
  }
}
