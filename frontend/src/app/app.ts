import { formatDate } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CharacterSummary, TopNavComponent } from '@ui';

import { WowheadTooltipLayer } from '@core/components/wowhead-tooltip-layer/wowhead-tooltip-layer';
import { AuthService } from '@core/services/auth.service';
import { MenuService } from '@core/services/menu.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { LocaleService } from '@core/services/locale.service';
import { AppLocale, isAppLocale } from '@core/services/locale-support';
import { ToastRegion } from '@core/components/toast-region/toast-region';

const FALLBACK_CHARACTER: CharacterSummary = {
  name: $localize`:@@app.fallbackCharacter.name:Adventurer`,
  realm: $localize`:@@app.fallbackCharacter.realm:No realm selected`,
  level: 0,
  profession: '',
  skill: '',
};

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, TopNavComponent, WowheadTooltipLayer, ToastRegion],
  template: `
    <div class="flex h-dvh flex-col overflow-hidden bg-background text-on-surface">
      <app-wowhead-tooltip-layer />
      <app-toast-region />
      <a
        href="#page-main"
        class="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-[200] focus:rounded-md focus:bg-primary focus:px-3 focus:py-2 focus:font-medium focus:text-on-primary focus:shadow-lg"
      >
        <ng-container i18n="@@app.skipToMain">Skip to main content</ng-container>
      </a>
      <ee-top-nav
        [items]="menu.links()"
        [activeId]="'dashboard'"
        [character]="character()"
        [accountRouterLink]="accountRouterLink()"
        [accountQueryParams]="accountQueryParams()"
        [accountLabel]="accountLabel()"
        [localeOptions]="locale.localeOptions"
        [activeLocale]="locale.activeLocale()"
        [mobileDrawerOpen]="mobileNavOpen()"
        [subText]="lastModifiedSubText()"
        (toggleMobileDrawer)="toggleMobileNav()"
        (navSelected)="onPrimaryNavSelected($event)"
        (localeSelected)="onLocaleSelected($event)"
      />
      <div class="flex min-h-0 min-w-0 flex-1 flex-col">
        <router-outlet />
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  readonly menu = inject(MenuService);
  private readonly auth = inject(AuthService);
  private readonly realmSelection = inject(RealmSelectionService);
  protected readonly locale = inject(LocaleService);
  protected readonly mobileNavOpen = signal(false);
  readonly commodityDetails = this.realmSelection.commodityDetails.asReadonly();
  readonly auctionHouseDetails = this.realmSelection.auctionHouseDetails.asReadonly();
  readonly isSameTimeForRealmAndCommodity = computed(() => {
    const commodityTime = new Date(this.commodityDetails()?.lastModified || 0);
    const houseTime = new Date(this.auctionHouseDetails()?.lastModified || 0);
    return +commodityTime === +houseTime;
  });
  protected readonly lastModifiedSubText = computed(() => {
    const auctionHouseLastModified = this.auctionHouseDetails()?.lastModified;
    const commodityLastModified = this.commodityDetails()?.lastModified;
    const auctionHouseText = this.formatLastModified(auctionHouseLastModified);
    const commodityText = this.formatLastModified(commodityLastModified);
    const lastModifiedText = $localize`:@@common.lastModified:Last modified` + ': ';

    if (!auctionHouseText) return lastModifiedText + commodityText;
    if (!commodityText) return lastModifiedText + auctionHouseText;
    const lastModifiedTime = this.isSameTimeForRealmAndCommodity()
      ? `${commodityText} - ${auctionHouseText}`
      : auctionHouseText;

    return lastModifiedText + lastModifiedTime;
  });

  constructor() {
    afterNextRender(() => {
      void this.auth.whenReady();
    });
  }

  protected readonly character = computed<CharacterSummary>(() => {
    const realm = this.realmSelection.selected();
    if (!realm) return FALLBACK_CHARACTER;
    return {
      ...FALLBACK_CHARACTER,
      realm: `${realm.name}-${realm.region.toUpperCase()}`,
    };
  });

  protected readonly accountRouterLink = computed(() => '/profile');

  protected readonly accountQueryParams = computed(() => null);

  protected readonly accountLabel = computed(() =>
    this.auth.user()
      ? $localize`:@@app.account.openProfile:Open profile`
      : $localize`:@@app.account.openProfile:Open profile`,
  );

  protected toggleMobileNav(): void {
    this.mobileNavOpen.update((open) => !open);
  }

  protected onPrimaryNavSelected(id: string): void {
    console.log(id);
  }

  protected onLocaleSelected(locale: string): void {
    if (isAppLocale(locale)) {
      this.locale.switchLocale(locale as AppLocale);
    }
  }

  private formatLastModified(value: string | null | undefined): string {
    return value ? formatDate(value, 'short', this.locale.formatLocale()) : '';
  }
}
