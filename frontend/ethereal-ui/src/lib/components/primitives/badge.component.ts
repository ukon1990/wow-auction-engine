import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { ItemQuality } from '../../models/ui-models';
import { qualityToneClasses } from '../../helpers/quality';

@Component({
  selector: 'ee-badge',
  template: ` <span [class]="badgeClass()">
    <ng-content />
  </span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BadgeComponent {
  readonly quality = input<ItemQuality>('rare');

  protected badgeClass(): string {
    return `inline-flex rounded border px-2 py-1 ee-label ${qualityToneClasses(this.quality())}`;
  }
}
