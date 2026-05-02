import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { CharacterSummary, NavItem } from '../../models/ui-models';
import { IconButtonComponent } from '../primitives/icon-button.component';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';

@Component({
  selector: 'ee-top-nav',
  imports: [IconButtonComponent, SymbolIconComponent, RouterLink, RouterLinkActive],
  template: `
    <header
      class="sticky top-0 z-50 flex h-16 w-full items-center justify-between gap-2 border-b border-white/10 bg-slate-950/80 px-3 shadow-[0_4px_20px_rgba(0,0,0,0.5)] backdrop-blur-xl sm:px-6"
    >
      <div class="flex min-w-0 flex-1 items-center gap-3 md:gap-8">
        <button
          type="button"
          class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-white/10 text-primary transition hover:bg-white/5 md:hidden"
          [attr.aria-expanded]="mobileDrawerOpen()"
          aria-controls="ee-side-nav-drawer"
          aria-label="Open navigation menu"
          (click)="toggleMobileDrawer.emit()"
        >
          <ee-symbol-icon class="text-[22px]" name="menu" />
        </button>
        <div
          class="min-w-0 truncate font-cinzel text-lg font-bold tracking-[0.05em] text-primary-container drop-shadow-[0_0_8px_rgba(236,185,19,0.4)] sm:text-xl md:text-2xl"
        >
          The Ethereal Exchange
        </div>
        <nav
          class="hidden min-w-0 items-center gap-4 lg:gap-6 md:flex"
          aria-label="Primary navigation"
        >
          @for (item of items(); track item.id) {
            @if (item.routerLink) {
              <a
                [routerLink]="item.routerLink"
                routerLinkActive="border-b-2 border-primary-container text-primary-container"
                [routerLinkActiveOptions]="{ exact: true }"
                [class]="navLinkClass()"
                [attr.aria-current]="item.id === activeId() ? 'page' : null"
              >
                {{ item.label }}
              </a>
            } @else {
              <button type="button" [class]="navClass(item.id)" (click)="onPrimaryButton(item.id)">
                {{ item.label }}
              </button>
            }
          }
        </nav>
      </div>
      <div class="flex shrink-0 items-center gap-1 sm:gap-3">
        <span class="hidden sm:inline-flex">
          <ee-icon-button icon="account_circle" label="Account" />
        </span>
        <ee-icon-button icon="settings" label="Settings" />
        <span class="hidden md:inline-flex">
          <ee-icon-button icon="query_stats" label="Analytics" />
        </span>
        <div
          class="hidden h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 border-primary bg-surface-container text-primary sm:flex"
          [attr.aria-label]="characterLabel()"
        >
          <ee-symbol-icon class="text-[18px]" name="person" />
        </div>
      </div>
    </header>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopNavComponent {
  readonly items = input.required<readonly NavItem[]>();
  readonly activeId = input.required<string>();
  readonly character = input.required<CharacterSummary>();
  readonly mobileDrawerOpen = input(false);
  readonly navSelected = output<string>();
  readonly toggleMobileDrawer = output<void>();

  protected navClass(id: string): string {
    const base =
      'rounded px-1 py-2 font-cinzel text-sm font-bold uppercase tracking-wide transition hover:bg-white/5';
    return id === this.activeId()
      ? `${base} border-b-2 border-primary-container text-primary-container`
      : `${base} text-slate-400 hover:text-on-surface`;
  }

  protected navLinkClass(): string {
    return 'rounded px-1 py-2 font-cinzel text-sm font-bold uppercase tracking-wide transition hover:bg-white/5 text-slate-400 hover:text-on-surface';
  }

  protected characterLabel(): string {
    const character = this.character();
    return `${character.name}, level ${character.level}, ${character.realm}`;
  }

  protected onPrimaryButton(id: string): void {
    this.navSelected.emit(id);
  }
}
