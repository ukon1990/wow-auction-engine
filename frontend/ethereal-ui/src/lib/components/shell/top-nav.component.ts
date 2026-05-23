import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  HostListener,
  inject,
  input,
  OnDestroy,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Params, RouterLink, RouterLinkActive } from '@angular/router';

import { CharacterSummary, LocaleOption, NavItem } from '../../models/ui-models';
import { IconButtonComponent } from '../primitives/icon-button.component';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';
import { TopNavDropdownItemComponent } from './top-nav-dropdown-item.component';
import { TopNavItemComponent } from './top-nav-item.component';

@Component({
  selector: 'ee-top-nav',
  imports: [
    IconButtonComponent,
    NgTemplateOutlet,
    RouterLinkActive,
    SymbolIconComponent,
    RouterLink,
    TopNavDropdownItemComponent,
    TopNavItemComponent,
  ],
  template: `
    <header
      #header
      class="sticky top-0 z-50 flex h-16 w-full items-center justify-between gap-2 border-b border-white/10 bg-slate-950/80 px-3 shadow-[0_4px_20px_rgba(0,0,0,0.5)] backdrop-blur-xl sm:px-6"
    >
      <div #leftCluster class="flex min-w-0 flex-1 items-center gap-3 md:gap-8">
        @if (useMobileNavigation()) {
          <button
            #menuButton
            type="button"
            class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-white/10 text-primary transition hover:bg-white/5"
            [attr.aria-expanded]="mobileDrawerOpen()"
            aria-controls="ee-side-nav-drawer"
            aria-label="Open navigation menu"
            i18n-aria-label="@@topNav.openNavigation"
            (click)="toggleMobileDrawer.emit()"
          >
            <ee-symbol-icon class="text-[22px]" name="menu" />
          </button>
        }
        <div #brandBlock class="flex min-w-0 flex-col">
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
          #desktopNav
          [class.flex]="!useMobileNavigation()"
          [class.hidden]="useMobileNavigation()"
          class="min-w-0 items-center gap-4 lg:gap-6"
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
      <div
        #actionCluster
        [class.flex]="!useMobileNavigation()"
        [class.hidden]="useMobileNavigation()"
        class="shrink-0 items-center gap-1 sm:gap-3"
      >
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
      <nav
        #measureNav
        class="pointer-events-none invisible absolute -left-[9999px] top-0 flex items-center gap-4 whitespace-nowrap lg:gap-6"
        aria-hidden="true"
        inert
      >
        @for (item of items(); track item.id) {
          @if (hasChildren(item)) {
            <ee-top-nav-dropdown-item
              [item]="item"
              [activeId]="activeId()"
              [open]="false"
            />
          } @else {
            <ee-top-nav-item
              [item]="item"
              [activeId]="activeId()"
            />
          }
        }
      </nav>
    </header>
    @if (useMobileNavigation() && mobileDrawerOpen()) {
      <button
        type="button"
        class="fixed inset-0 top-16 z-40 cursor-default bg-black/50 backdrop-blur-sm"
        aria-label="Close navigation menu"
        (click)="toggleMobileDrawer.emit()"
      ></button>
      <nav
        id="ee-side-nav-drawer"
        class="fixed inset-x-3 top-[4.75rem] z-50 max-h-[calc(100dvh-5.5rem)] overflow-y-auto rounded border border-white/10 bg-surface-container shadow-[0_18px_50px_rgba(0,0,0,0.55)] sm:left-auto sm:w-80"
        aria-label="Primary navigation"
        i18n-aria-label="@@topNav.primaryNavigation"
      >
        <div class="border-b border-white/10 px-4 py-3 font-cinzel text-sm font-bold uppercase tracking-wide text-primary-container">
          Menu
        </div>
        @if (items().length) {
          <div class="py-2">
            @for (item of items(); track item.id) {
              @if (hasChildren(item)) {
                <section class="border-t border-white/10 first:border-t-0">
                  <div class="flex items-center gap-3 px-4 py-3 font-cinzel text-xs font-bold uppercase tracking-wide text-slate-400">
                    <ee-symbol-icon class="text-[18px]" [name]="item.icon" />
                    <span>{{ item.label }}</span>
                  </div>
                  @for (child of item.children ?? []; track child.id) {
                    <ng-container
                      [ngTemplateOutlet]="mobileNavItem"
                      [ngTemplateOutletContext]="{ item: child }"
                    />
                  }
                </section>
              } @else {
                <ng-container
                  [ngTemplateOutlet]="mobileNavItem"
                  [ngTemplateOutletContext]="{ item: item }"
                />
              }
            }
          </div>
        } @else {
          <p class="px-4 py-5 text-sm text-on-surface-variant">
            No navigation items available.
          </p>
        }
        <div class="border-t border-white/10 p-4">
          <label
            class="mb-2 block text-xs font-bold uppercase tracking-wide text-slate-400"
            for="ee-mobile-locale-select"
            i18n="@@topNav.language"
          >
            Language
          </label>
          <select
            id="ee-mobile-locale-select"
            class="mb-3 h-10 w-full rounded border border-white/10 bg-surface-container px-3 ee-label text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
            [value]="activeLocale()"
            aria-label="Language"
            i18n-aria-label="@@topNav.language"
            (change)="onMobileLocaleChange($event)"
          >
            @for (locale of localeOptions(); track locale.id) {
              <option [value]="locale.id" [selected]="locale.id === activeLocale()">
                {{ locale.label }}
              </option>
            }
          </select>
          <div class="grid grid-cols-3 gap-2">
            <button
              type="button"
              class="inline-flex h-10 items-center justify-center rounded border border-white/10 text-primary transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
              aria-label="Settings"
              i18n-aria-label="@@topNav.settings"
              (click)="closeMobileNavigation()"
            >
              <ee-symbol-icon class="text-[18px]" name="settings" />
            </button>
            <button
              type="button"
              class="inline-flex h-10 items-center justify-center rounded border border-white/10 text-primary transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
              aria-label="Analytics"
              i18n-aria-label="@@topNav.analytics"
              (click)="closeMobileNavigation()"
            >
              <ee-symbol-icon class="text-[18px]" name="query_stats" />
            </button>
            <a
              class="inline-flex h-10 items-center justify-center rounded border border-white/10 text-primary transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
              [routerLink]="accountRouterLink()"
              [queryParams]="accountQueryParams()"
              [attr.aria-label]="accountLabel()"
              (click)="onMobileLinkSelected()"
            >
              <ee-symbol-icon class="text-[18px]" name="person" />
            </a>
          </div>
        </div>
      </nav>
    }
    <ng-template #mobileNavItem let-item="item">
      @if (item.routerLink) {
        <a
          class="flex w-full items-center gap-3 px-4 py-3 text-left font-cinzel text-sm font-bold uppercase tracking-wide text-slate-300 transition hover:bg-white/5 hover:text-on-surface"
          [routerLink]="item.routerLink"
          routerLinkActive
          [routerLinkActiveOptions]="{ exact: true }"
          #mobileRouteActive="routerLinkActive"
          [class.bg-yellow-500/10]="mobileRouteActive.isActive"
          [class.text-primary-container]="mobileRouteActive.isActive"
          [attr.aria-current]="mobileRouteActive.isActive ? 'page' : null"
          (click)="onMobileLinkSelected()"
        >
          @if (item.icon) {
            <ee-symbol-icon class="text-[18px]" [name]="item.icon" />
          }
          <span>{{ item.label }}</span>
        </a>
      } @else {
        <button
          type="button"
          class="flex w-full items-center gap-3 px-4 py-3 text-left font-cinzel text-sm font-bold uppercase tracking-wide text-slate-300 transition hover:bg-white/5 hover:text-on-surface"
          [class.bg-yellow-500/10]="item.id === activeId()"
          [class.text-primary-container]="item.id === activeId()"
          (click)="onMobilePrimaryButton(item.id)"
        >
          @if (item.icon) {
            <ee-symbol-icon class="text-[18px]" [name]="item.icon" />
          }
          <span>{{ item.label }}</span>
        </button>
      }
    </ng-template>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopNavComponent implements OnDestroy {
  private static readonly FIT_BUFFER_PX = 12;
  private static readonly MIN_DESKTOP_WIDTH_PX = 768;

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly header = viewChild<ElementRef<HTMLElement>>('header');
  private readonly leftCluster = viewChild<ElementRef<HTMLElement>>('leftCluster');
  private readonly brandBlock = viewChild<ElementRef<HTMLElement>>('brandBlock');
  private readonly actionCluster = viewChild<ElementRef<HTMLElement>>('actionCluster');
  private readonly measureNav = viewChild<ElementRef<HTMLElement>>('measureNav');

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
  protected readonly useMobileNavigation = signal(true);

  private resizeObserver: ResizeObserver | null = null;
  private measureFrame = 0;

  constructor() {
    afterNextRender(() => {
      this.resizeObserver = new ResizeObserver(() => {
        this.scheduleNavigationMeasurement();
      });
      for (const element of this.observedElements()) {
        this.resizeObserver.observe(element);
      }
      this.scheduleNavigationMeasurement();
    });

    effect(() => {
      this.items();
      this.localeOptions();
      this.activeLocale();
      this.subText();
      this.accountLabel();
      this.scheduleNavigationMeasurement();
    });
  }

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

  @HostListener('window:resize')
  protected onWindowResize(): void {
    this.scheduleNavigationMeasurement();
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

  protected onMobilePrimaryButton(id: string): void {
    this.navSelected.emit(id);
    this.closeMobileNavigation();
  }

  protected onMobileLinkSelected(): void {
    this.closeMobileNavigation();
  }

  protected onLocaleChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    if (select?.value) {
      this.localeSelected.emit(select.value);
    }
  }

  protected onMobileLocaleChange(event: Event): void {
    this.onLocaleChange(event);
    this.closeMobileNavigation();
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    if (this.measureFrame) {
      cancelAnimationFrame(this.measureFrame);
    }
  }

  private scheduleNavigationMeasurement(): void {
    if (typeof requestAnimationFrame !== 'function') {
      return;
    }
    cancelAnimationFrame(this.measureFrame);
    this.measureFrame = requestAnimationFrame(() => {
      this.measureFrame = 0;
      this.updateNavigationMode();
    });
  }

  private updateNavigationMode(): void {
    const header = this.header()?.nativeElement;
    const leftCluster = this.leftCluster()?.nativeElement;
    const brandBlock = this.brandBlock()?.nativeElement;
    const actionCluster = this.actionCluster()?.nativeElement;
    const measureNav = this.measureNav()?.nativeElement;
    if (!header || !leftCluster || !brandBlock || !actionCluster || !measureNav) {
      return;
    }

    const headerStyles = getComputedStyle(header);
    const leftStyles = getComputedStyle(leftCluster);
    const headerHorizontalPadding =
      this.cssPixels(headerStyles.paddingLeft) + this.cssPixels(headerStyles.paddingRight);
    const headerGap = this.cssPixels(headerStyles.columnGap || headerStyles.gap);
    const leftGap = this.cssPixels(leftStyles.columnGap || leftStyles.gap);

    const availableWidth =
      header.clientWidth -
      headerHorizontalPadding -
      (this.useMobileNavigation() ? 0 : actionCluster.offsetWidth) -
      headerGap -
      TopNavComponent.FIT_BUFFER_PX;
    const requiredWidth = brandBlock.scrollWidth + measureNav.scrollWidth + leftGap;
    const nextUseMobileNavigation =
      header.clientWidth < TopNavComponent.MIN_DESKTOP_WIDTH_PX || requiredWidth > availableWidth;

    if (this.useMobileNavigation() !== nextUseMobileNavigation) {
      this.useMobileNavigation.set(nextUseMobileNavigation);
      if (nextUseMobileNavigation) {
        this.closeDropdown();
      } else if (this.mobileDrawerOpen()) {
        this.toggleMobileDrawer.emit();
      }
    }
  }

  protected closeMobileNavigation(): void {
    if (this.mobileDrawerOpen()) {
      this.toggleMobileDrawer.emit();
    }
  }

  private observedElements(): HTMLElement[] {
    return [
      this.header()?.nativeElement,
      this.leftCluster()?.nativeElement,
      this.brandBlock()?.nativeElement,
      this.actionCluster()?.nativeElement,
      this.measureNav()?.nativeElement,
    ].filter((element): element is HTMLElement => Boolean(element));
  }

  private cssPixels(value: string): number {
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
}
