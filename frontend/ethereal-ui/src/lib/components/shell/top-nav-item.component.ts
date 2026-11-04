import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { NavItem } from '../../models/ui-models';

/** One page link in the top bar. The current page is gold with an underline. */
@Component({
  selector: 'ee-top-nav-item',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <a
      class="inline-flex h-16 items-center border-b-2 border-transparent px-2 text-sm font-medium text-on-surface-variant transition hover:text-on-surface"
      [routerLink]="item().routerLink ?? null"
      routerLinkActive="!border-primary !text-primary"
      #routeActive="routerLinkActive"
      [attr.aria-current]="routeActive.isActive ? 'page' : null"
    >
      {{ item().label }}
    </a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopNavItemComponent {
  readonly item = input.required<NavItem>();
}
