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

const FALLBACK_CHARACTER: CharacterSummary = {
  name: 'Adventurer',
  realm: 'No realm selected',
  level: 0,
  profession: '',
  skill: '',
};

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, TopNavComponent, WowheadTooltipLayer],
  template: `
    <div class="flex h-dvh flex-col overflow-hidden bg-background text-on-surface">
      <app-wowhead-tooltip-layer />
      <a
        href="#page-main"
        class="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-[200] focus:rounded-md focus:bg-primary focus:px-3 focus:py-2 focus:font-medium focus:text-on-primary focus:shadow-lg"
      >
        Skip to main content
      </a>
      <ee-top-nav
        [items]="menu.links()"
        [activeId]="'dashboard'"
        [character]="character()"
        [accountRouterLink]="accountRouterLink()"
        [accountQueryParams]="accountQueryParams()"
        [accountLabel]="accountLabel()"
        [mobileDrawerOpen]="mobileNavOpen()"
        (toggleMobileDrawer)="toggleMobileNav()"
        (navSelected)="onPrimaryNavSelected($event)"
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
  protected readonly mobileNavOpen = signal(false);

  constructor() {
    afterNextRender(() => {
      void this.auth.refresh();
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

  protected readonly accountLabel = computed(() => (this.auth.user() ? 'Open profile' : 'Sign in'));

  protected toggleMobileNav(): void {
    this.mobileNavOpen.update((open) => !open);
  }

  protected onPrimaryNavSelected(id: string): void {
    console.log(id);
  }

  private returnToUrl(): string {
    const url = this.router.url || '/';
    return url.startsWith('/login') ? '/' : url;
  }
}
