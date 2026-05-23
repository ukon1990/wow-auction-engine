import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { Params, RouterLink } from '@angular/router';

import { CharacterSummary, LocaleOption, NavItem } from '../../models/ui-models';
import { IconButtonComponent } from '../primitives/icon-button.component';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';
import { TopNavDropdownItemComponent } from './top-nav-dropdown-item.component';
import { TopNavItemComponent } from './top-nav-item.component';

@Component({
  selector: 'ee-top-nav',
  imports: [
    IconButtonComponent,
    SymbolIconComponent,
    RouterLink,
    TopNavDropdownItemComponent,
    TopNavItemComponent,
  ],
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
          i18n-aria-label="@@topNav.openNavigation"
          (click)="toggleMobileDrawer.emit()"
        >
          <ee-symbol-icon class="text-[22px]" name="menu" />
        </button>
        <div class="flex min-w-0 flex-col">
          <div
            class="min-w-0 truncate font-cinzel text-lg font-bold tracking-[0.05em] text-primary-container drop-shadow-[0_0_8px_rgba(236,185,19,0.4)] sm:text-xl md:text-2xl"
          >
            <ng-container i18n="@@brand.name">The Ethereal Exchange</ng-container>
          </div>
          @if (subText()) {
            <small
              class="min-w-0 truncate text-[0.7rem] font-medium leading-3 tracking-normal text-on-surface-variant sm:text-xs"
            >
              {{ subText() }}
            </small>
          }
        </div>
        <nav
          class="hidden min-w-0 items-center gap-4 lg:gap-6 md:flex"
          aria-label="Primary navigation"
          i18n-aria-label="@@topNav.primaryNavigation"
        >
          @for (item of items(); track item.id) {
            @if (hasChildren(item)) {
              <ee-top-nav-dropdown-item
                [item]="item"
                [activeId]="activeId()"
                [open]="isDropdownOpen(item.id)"
                (toggle)="toggleDropdown(item.id)"
                (close)="closeDropdown()"
                (selected)="onDropdownButton($event)"
              />
            } @else {
              <ee-top-nav-item
                [item]="item"
                [activeId]="activeId()"
                (selected)="onPrimaryButton($event)"
              />
            }
          }
        </nav>
      </div>
      <div class="flex shrink-0 items-center gap-1 sm:gap-3">
        <label class="sr-only" for="ee-locale-select" i18n="@@topNav.language">Language</label>
        <select
          id="ee-locale-select"
          class="h-8 rounded border border-white/10 bg-surface-container px-2 ee-label text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
          [value]="activeLocale()"
          aria-label="Language"
          i18n-aria-label="@@topNav.language"
          (change)="onLocaleChange($event)"
        >
          @for (locale of localeOptions(); track locale.id) {
            <option [value]="locale.id" [selected]="locale.id === activeLocale()">
              {{ locale.label }}
            </option>
          }
        </select>
        <ee-icon-button icon="settings" i18n-label="@@topNav.settings" label="Settings" />
        <span class="hidden md:inline-flex">
          <ee-icon-button icon="query_stats" i18n-label="@@topNav.analytics" label="Analytics" />
        </span>
        <a
          class="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 border-primary bg-surface-container text-primary transition hover:bg-white/5 hover:text-on-surface focus:outline-none focus:ring-2 focus:ring-primary-container"
          [routerLink]="accountRouterLink()"
          [queryParams]="accountQueryParams()"
          [attr.aria-label]="accountLabel()"
        >
          <ee-symbol-icon class="text-[18px]" name="person" />
        </a>
      </div>
    </header>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopNavComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly items = input.required<readonly NavItem[]>();
  readonly activeId = input.required<string>();
  readonly character = input.required<CharacterSummary>();
  readonly accountRouterLink = input<string | readonly unknown[]>('/login');
  readonly accountQueryParams = input<Params | null>(null);
  readonly accountLabel = input('Account');
  readonly localeOptions = input<readonly LocaleOption[]>([]);
  readonly activeLocale = input('en');
  readonly mobileDrawerOpen = input(false);
  readonly navSelected = output<string>();
  readonly localeSelected = output<string>();
  readonly toggleMobileDrawer = output<void>();
  readonly subText = input<string>('');

  protected readonly openDropdownId = signal<string | null>(null);

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    const target = event.target;
    if (target instanceof Node && this.host.nativeElement.contains(target)) {
      return;
    }
    this.closeDropdown();
  }

  @HostListener('document:keydown.escape')
  protected onDocumentEscape(): void {
    this.closeDropdown();
  }

  protected onPrimaryButton(id: string): void {
    this.navSelected.emit(id);
  }

  protected hasChildren(item: NavItem): boolean {
    return Boolean(item.children?.length);
  }

  protected isDropdownOpen(id: string): boolean {
    return this.openDropdownId() === id;
  }

  protected toggleDropdown(id: string): void {
    this.openDropdownId.update((openId) => (openId === id ? null : id));
  }

  protected closeDropdown(): void {
    this.openDropdownId.set(null);
  }

  protected onDropdownButton(id: string): void {
    this.navSelected.emit(id);
    this.closeDropdown();
  }

  protected onLocaleChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    if (select?.value) {
      this.localeSelected.emit(select.value);
    }
  }
}
