import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SymbolIconComponent } from '../primitives/symbol-icon.component';

/** Top bar button that shows the selected realm and opens the realm picker. */
@Component({
  selector: 'ee-realm-switcher',
  imports: [RouterLink, SymbolIconComponent],
  template: `
    <a
      class="inline-flex h-10 min-w-0 max-w-full items-center gap-2 rounded-md border border-white/10 bg-surface-container px-2.5 text-on-surface transition hover:bg-surface-container-high"
      [routerLink]="routerLink()"
      [attr.aria-label]="label()"
      [attr.title]="label()"
    >
      @if (region()) {
        <span
          class="rounded bg-surface-container-highest px-1.5 py-0.5 text-[11px] font-semibold uppercase text-on-surface-variant"
          aria-hidden="true"
          >{{ region() }}</span
        >
      }
      <span class="min-w-0 truncate text-sm font-semibold">{{ realmName() }}</span>
      <ee-symbol-icon class="text-[18px] text-on-surface-variant" name="expand_more" />
    </a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RealmSwitcherComponent {
  readonly realmName = input.required<string>();
  readonly region = input('');
  readonly routerLink = input<string | readonly unknown[]>('/');
  readonly label = input('Change realm');
}
