import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { NavItem } from '../../models/ui-models';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';

/**
 * Side menu for a group of pages (the admin pages). A vertical list from `md` up,
 * a row that scrolls sideways on phones.
 */
@Component({
  selector: 'ee-section-nav',
  imports: [RouterLink, RouterLinkActive, SymbolIconComponent],
  host: {
    class:
      'block shrink-0 border-b border-white/10 bg-surface-container-lowest md:w-60 md:border-b-0 md:border-r',
  },
  template: `
    <nav class="flex h-full flex-col gap-1 md:p-3" [attr.aria-label]="heading()">
      <p class="ee-label hidden px-3 pb-1 pt-2 text-outline md:block">{{ heading() }}</p>
      <ul class="flex gap-1 overflow-x-auto px-3 py-2 md:flex-col md:overflow-visible md:p-0">
        @for (item of items(); track item.id) {
          <li class="shrink-0">
            <a
              class="flex items-center gap-2.5 whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium text-on-surface-variant transition hover:bg-white/5 hover:text-on-surface"
              [routerLink]="item.routerLink ?? null"
              routerLinkActive="!bg-surface-container-high !text-primary"
              #active="routerLinkActive"
              [attr.aria-current]="active.isActive ? 'page' : null"
            >
              <ee-symbol-icon class="text-[18px]" [name]="item.icon" />
              <span>{{ item.label }}</span>
            </a>
          </li>
        }
      </ul>
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SectionNavComponent {
  readonly items = input.required<readonly NavItem[]>();
  readonly heading = input('');
}
