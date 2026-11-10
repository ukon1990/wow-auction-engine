import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SectionNavComponent } from '@ui';

import { MenuService } from '@core/services/menu.service';

/** Layout for the admin pages: a side menu with every admin page next to the page. */
@Component({
  selector: 'app-admin-shell',
  imports: [RouterOutlet, SectionNavComponent],
  host: { class: 'flex min-h-0 min-w-0 flex-1 flex-col md:flex-row' },
  template: `
    <ee-section-nav heading="Admin" i18n-heading="@@route.admin.title" [items]="adminLinks()" />
    <div class="flex min-h-0 min-w-0 flex-1 flex-col">
      <router-outlet />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminShellPage {
  private readonly menu = inject(MenuService);

  protected readonly adminLinks = computed(
    () => this.menu.links().find((link) => link.id === 'admin')?.children ?? [],
  );
}
