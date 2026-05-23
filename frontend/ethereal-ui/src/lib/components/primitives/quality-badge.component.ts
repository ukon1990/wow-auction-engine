import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { ItemQuality } from '../../models/ui-models';
import { formatQuality } from '../../helpers/quality';
import { BadgeComponent } from './badge.component';

@Component({
  selector: 'ee-quality-badge',
  imports: [BadgeComponent],
  template: `<ee-badge [quality]="quality()">{{ label() }}</ee-badge>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QualityBadgeComponent {
  readonly quality = input.required<ItemQuality>();

  protected label(): string {
    return formatQuality(this.quality());
  }
}
