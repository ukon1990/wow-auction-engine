import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CharacterSummary, TopNavComponent } from '@ui';

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
  imports: [RouterOutlet, TopNavComponent],
  template: `
    <div class="flex h-screen flex-col overflow-hidden bg-background text-on-surface">
      <ee-top-nav
        [items]="menu.links()"
        [activeId]="'dashboard'"
        [character]="character()"
        [mobileDrawerOpen]="mobileNavOpen()"
        (toggleMobileDrawer)="toggleMobileNav()"
        (navSelected)="onPrimaryNavSelected($event)"
      />
      <router-outlet />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  readonly menu = inject(MenuService);
  private readonly realmSelection = inject(RealmSelectionService);
  protected readonly mobileNavOpen = signal(false);

  protected readonly character = computed<CharacterSummary>(() => {
    const realm = this.realmSelection.selected();
    if (!realm) return FALLBACK_CHARACTER;
    return {
      ...FALLBACK_CHARACTER,
      realm: `${realm.name}-${realm.region.toUpperCase()}`,
    };
  });

  protected toggleMobileNav(): void {
    this.mobileNavOpen.update((open) => !open);
  }

  protected onPrimaryNavSelected(id: string): void {
    console.log(id);
  }
}
