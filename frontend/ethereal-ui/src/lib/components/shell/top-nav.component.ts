import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Params, RouterLink } from '@angular/router';

import { LocaleOption, NavItem } from '../../models/ui-models';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';
import { TopNavItemComponent } from './top-nav-item.component';

/**
 * The sticky top bar: wordmark, main pages, language and account.
 * Project the realm switcher with `eeTopNavStart` (phones) and `eeTopNavStartDesktop`,
 * and other actions (like data freshness) as default content.
 * On phones the main pages move to `ee-mobile-tab-bar`.
 */
@Component({
  selector: 'ee-top-nav',
  imports: [RouterLink, SymbolIconComponent, TopNavItemComponent],
  templateUrl: './top-nav.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopNavComponent {
  readonly items = input.required<readonly NavItem[]>();
  readonly homeRouterLink = input<string | readonly unknown[]>('/');
  readonly accountRouterLink = input<string | readonly unknown[]>('/login');
  readonly accountQueryParams = input<Params | null>(null);
  readonly accountLabel = input('Account');
  readonly localeOptions = input<readonly LocaleOption[]>([]);
  readonly activeLocale = input('en');
  readonly localeSelected = output<string>();

  protected onLocaleChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    if (select?.value) {
      this.localeSelected.emit(select.value);
    }
  }
}
