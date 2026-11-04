import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { NavItem } from '../../models/ui-models';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';

/** Bottom navigation for phones. Hidden from the `md` breakpoint up. */
@Component({
  selector: 'ee-mobile-tab-bar',
  imports: [RouterLink, RouterLinkActive, SymbolIconComponent],
  host: { class: 'block md:hidden' },
  template: `
    <nav
      class="flex border-t border-white/10 bg-surface-container-lowest pb-[env(safe-area-inset-bottom)]"
      [attr.aria-label]="label()"
    >
      @for (item of items(); track item.id) {
        <a
          class="flex min-w-0 flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-semibold text-outline transition hover:text-on-surface"
          [routerLink]="item.routerLink ?? null"
          routerLinkActive="!text-primary"
          #active="routerLinkActive"
          [attr.aria-current]="active.isActive ? 'page' : null"
        >
          <ee-symbol-icon class="text-[22px]" [name]="item.icon" />
          <span class="max-w-full truncate">{{ item.label }}</span>
        </a>
      }
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MobileTabBarComponent {
  readonly items = input.required<readonly NavItem[]>();
  readonly label = input('Main');
}
