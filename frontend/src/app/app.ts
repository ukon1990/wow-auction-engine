import { formatDate, isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {
  DataFreshnessComponent,
  MobileTabBarComponent,
  NavItem,
  RealmSwitcherComponent,
  TopNavComponent,
} from '@ui';

import { WowheadTooltipLayer } from '@core/components/wowhead-tooltip-layer/wowhead-tooltip-layer';
import { AuthService } from '@core/services/auth.service';
import { MenuService } from '@core/services/menu.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { LocaleService } from '@core/services/locale.service';
import { AppLocale, isAppLocale } from '@core/services/locale-support';
import { ToastRegion } from '@core/components/toast-region/toast-region';
import { ItemDetailModalHostComponent } from '@shared/item-detail-modal/item-detail-modal.host';
import { formatDataAge } from '@core/utils/data-age';

/** Data older than this is shown as stale in the top bar. */
const STALE_AFTER_MS = 2 * 60 * 60 * 1000;

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    TopNavComponent,
    RealmSwitcherComponent,
    DataFreshnessComponent,
    MobileTabBarComponent,
    WowheadTooltipLayer,
    ToastRegion,
    ItemDetailModalHostComponent,
  ],
  template: `
    <div class="flex h-dvh flex-col overflow-hidden bg-background text-on-surface">
      <app-wowhead-tooltip-layer />
      <app-toast-region />
      <app-item-detail-modal-host />
      <a
        href="#page-main"
        class="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-[200] focus:rounded-md focus:bg-primary focus:px-3 focus:py-2 focus:font-medium focus:text-on-primary focus:shadow-lg"
      >
        <ng-container i18n="@@app.skipToMain">Skip to main content</ng-container>
      </a>
      <ee-top-nav
        [items]="navItems()"
        [accountRouterLink]="accountRouterLink()"
        [accountLabel]="accountLabel()"
        [localeOptions]="locale.localeOptions"
        [activeLocale]="locale.activeLocale()"
        (localeSelected)="onLocaleSelected($event)"
      >
        @if (realm(); as selected) {
          <ee-realm-switcher
            eeTopNavStart
            [realmName]="selected.name"
            [region]="selected.region"
            label="Change realm"
            i18n-label="@@app.realmSwitcher.label"
          />
        }
        @if (realm(); as selected) {
          <ee-realm-switcher
            eeTopNavStartDesktop
            [realmName]="selected.name"
            [region]="selected.region"
            label="Change realm"
            i18n-label="@@app.realmSwitcher.label"
          />
        }
        @if (dataAge(); as age) {
          <ee-data-freshness
            label="Updated"
            i18n-label="@@app.dataFreshness.label"
            [age]="age"
            [details]="dataAgeDetails()"
            [state]="dataIsStale() ? 'stale' : 'fresh'"
          />
        }
      </ee-top-nav>
      <div class="flex min-h-0 min-w-0 flex-1 flex-col">
        <router-outlet />
      </div>
      <ee-mobile-tab-bar [items]="tabBarItems()" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  readonly menu = inject(MenuService);
  private readonly auth = inject(AuthService);
  private readonly realmSelection = inject(RealmSelectionService);
  protected readonly locale = inject(LocaleService);
  private readonly platformId = inject(PLATFORM_ID);
  readonly commodityDetails = this.realmSelection.commodityDetails.asReadonly();
  readonly auctionHouseDetails = this.realmSelection.auctionHouseDetails.asReadonly();
  /** Current time, updated every 30 seconds in the browser. Null during server rendering. */
  private readonly now = signal<number | null>(null);

  protected readonly realm = computed(() => {
    const selected = this.realmSelection.selected();
    return selected?.name ? selected : null;
  });

  /** The oldest of the realm and commodity snapshots, so the label never overstates freshness. */
  private readonly oldestUpdate = computed(() => {
    const times = [this.auctionHouseDetails()?.lastModified, this.commodityDetails()?.lastModified]
      .filter((value): value is string => Boolean(value))
      .map((value) => new Date(value).getTime())
      .filter((value) => Number.isFinite(value));
    return times.length ? Math.min(...times) : null;
  });

  protected readonly dataAge = computed(() => {
    const oldest = this.oldestUpdate();
    const now = this.now();
    if (oldest === null || now === null) return null;
    return formatDataAge(now - oldest);
  });

  protected readonly dataIsStale = computed(() => {
    const oldest = this.oldestUpdate();
    const now = this.now();
    return oldest !== null && now !== null && now - oldest > STALE_AFTER_MS;
  });

  protected readonly dataAgeDetails = computed(() => {
    const realm = this.formatLastModified(this.auctionHouseDetails()?.lastModified);
    const commodity = this.formatLastModified(this.commodityDetails()?.lastModified);
    return [
      realm ? $localize`:@@app.dataFreshness.realm:Realm auction house: ${realm}:time:` : '',
      commodity
        ? $localize`:@@app.dataFreshness.commodity:Region commodities: ${commodity}:time:`
        : '',
    ]
      .filter(Boolean)
      .join('. ');
  });

  /** Main pages, with Admin last. */
  protected readonly navItems = computed<NavItem[]>(() => {
    const links = this.menu.links();
    return [
      ...links.filter((link) => link.id !== 'admin'),
      ...links.filter((link) => link.id === 'admin'),
    ];
  });

  protected readonly tabBarItems = computed<NavItem[]>(() => [
    ...this.navItems(),
    {
      id: 'profile',
      label: $localize`:@@route.profile:Profile`,
      icon: 'person',
      routerLink: '/profile',
    },
  ]);

  constructor() {
    afterNextRender(() => {
      void this.auth.whenReady();
    });
    if (isPlatformBrowser(this.platformId)) {
      this.now.set(Date.now());
      const timer = setInterval(() => this.now.set(Date.now()), 30_000);
      inject(DestroyRef).onDestroy(() => clearInterval(timer));
    }
  }

  protected readonly accountRouterLink = computed(() => '/profile');

  protected readonly accountLabel = computed(
    () => $localize`:@@app.account.openProfile:Open profile`,
  );

  protected onLocaleSelected(locale: string): void {
    if (isAppLocale(locale)) {
      this.locale.switchLocale(locale as AppLocale);
    }
  }

  private formatLastModified(value: string | null | undefined): string {
    return value ? formatDate(value, 'short', this.locale.formatLocale()) : '';
  }
}
