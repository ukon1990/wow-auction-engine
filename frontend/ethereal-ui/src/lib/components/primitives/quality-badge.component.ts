import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { ItemQuality } from '../../models/ui-models';
import { formatQuality, qualityToneClasses } from '../../helpers/quality';

@Component({
  selector: 'ee-quality-badge',
  template: `<span [class]="badgeClass()">{{ label() }}</span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QualityBadgeComponent {
  readonly quality = input.required<ItemQuality>();

  protected label(): string {
    return formatQuality(this.quality());
  }

  protected badgeClass(): string {
    return `inline-flex rounded border px-2 py-1 ee-label ${qualityToneClasses(this.quality())}`;
  }
}
