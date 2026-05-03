import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterOutlet } from '@angular/router';
import { CharacterSummary, TopNavComponent } from '@ui';
import { Menu } from './core/services/menu';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, TopNavComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  readonly menu = inject(Menu);
  private readonly activeRoute = inject(ActivatedRoute);
  private readonly mobileNavOpen = signal(false);
  private readonly toggleMobileNav = () => this.mobileNavOpen.set(!this.mobileNavOpen());
  private readonly onPrimaryNavSelected = (id: string) => {
    console.log(id);
  };
  readonly character = signal<CharacterSummary>({
    name: 'GoblinKing99',
    realm: 'Illidan-US',
    level: 70,
    profession: 'Blacksmithing',
    skill: 'Skill Level 300/300',
  });
  private readonly $activeRoute = this.activeRoute.snapshot.url;
}
