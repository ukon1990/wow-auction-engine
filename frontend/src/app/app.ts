import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CharacterSummary, TopNavComponent } from '@ui';
import { MenuService } from './core/services/menu.service';

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
  protected readonly mobileNavOpen = signal(false);

  protected toggleMobileNav(): void {
    this.mobileNavOpen.update((open) => !open);
  }

  protected onPrimaryNavSelected(id: string): void {
    console.log(id);
  }
  readonly character = signal<CharacterSummary>({
    name: 'GoblinKing99',
    realm: 'Illidan-US',
    level: 70,
    profession: 'Blacksmithing',
    skill: 'Skill Level 300/300',
  });
}
