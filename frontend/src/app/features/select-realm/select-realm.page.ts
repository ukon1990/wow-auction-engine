import { isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { SearchInputComponent, SkeletonDirective } from '@ui';

import { Realm } from '@api/generated';
import { AuthService } from '@core/services/auth.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';

@Component({
  selector: 'app-select-realm-page',
  imports: [RouterLink, SearchInputComponent, SkeletonDirective],
  templateUrl: './select-realm.page.html',
  host: {
    class: 'flex min-h-0 flex-1 flex-col',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectRealmPage {
  private readonly auth = inject(AuthService);
  private readonly selection = inject(RealmSelectionService);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly query = signal('');
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly realms = this.selection.realms;
  protected readonly showSignIn = computed(() => this.auth.loaded() && !this.auth.user());
  protected readonly filtered = computed<readonly Realm[]>(() => {
    const needle = this.query().trim().toLowerCase();
    const list = this.realms();
    if (!needle) return list.slice(0, 50);
    return list
      .filter(
        (realm) =>
          realm.name.toLowerCase().includes(needle) ||
          realm.slug.toLowerCase().includes(needle) ||
          realm.region.toLowerCase().includes(needle),
      )
      .slice(0, 50);
  });

  protected readonly resultsLabel = computed(() => {
    const total = this.realms().length;
    const shown = this.filtered().length;
    if (total === 0) return '';
    return shown < total
      ? $localize`:@@realmPicker.resultsPartial:Showing ${shown} of ${total} realms`
      : $localize`:@@realmPicker.resultsTotal:${total} realms`;
  });

  constructor() {
    afterNextRender(() => {
      if (!this.auth.loaded()) {
        void this.auth.refresh();
      }
    });

    if (!isPlatformBrowser(this.platformId)) {
      this.loading.set(false);
      return;
    }
    this.selection
      .ensureCatalogLoaded()
      .then(() => this.loading.set(false))
      .catch((err: unknown) => {
        console.error('Failed to load realms', err);
        this.error.set(
          $localize`:@@realmPicker.loadError:Could not load realm list. Please try again later.`,
        );
        this.loading.set(false);
      });
  }

  protected onQueryChanged(value: string): void {
    this.query.set(value);
  }

  protected rememberSelection(realm: Realm): void {
    this.selection.select(realm);
  }

  protected trackByKey(_: number, realm: Realm): string {
    return `${realm.region}:${realm.slug}`;
  }
}
