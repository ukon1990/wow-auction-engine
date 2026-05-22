import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Params, Router, RouterOutlet } from '@angular/router';
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
  private readonly router = inject(Router);
  protected readonly locale = inject(LocaleService);
  protected readonly mobileNavOpen = signal(false);

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

  protected readonly accountRouterLink = computed(() => (this.auth.user() ? '/profile' : '/login'));

  protected readonly accountQueryParams = computed<Params | null>(() =>
    this.auth.user()
      ? null
      : {
          returnTo: this.returnToUrl(),
        },
  );

  protected readonly accountLabel = computed(() =>
    this.auth.user()
      ? $localize`:@@app.account.openProfile:Open profile`
      : $localize`:@@app.account.signIn:Sign in`,
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

  private returnToUrl(): string {
    const url = this.router.url || '/';
    return url.startsWith('/login') ? '/' : url;
  }
}
