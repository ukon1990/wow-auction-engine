import { isPlatformBrowser } from '@angular/common';
import { DestroyRef, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';

import { Realm, RealmApiService, RealmDetail } from '@api/generated';

const STORAGE_KEY = 'wae.selectedRealm';

/** Matches `/:region/:slug` at the start of the URL (region = Blizzard API code). */
const REALM_PATH = /^\/(us|eu|kr|tw)\/([^/]+)/i;

interface PersistedSelection {
  readonly region: Realm.RegionEnum;
  readonly slug: string;
}

function composeMarketDataVersion(detail: RealmDetail): string {
  const ah = detail.auctionHouse?.lastModified ?? '';
  const commodity = detail.commodity?.lastModified ?? '';
  return `ah=${ah}|commodity=${commodity}`;
}

function toRegionEnum(region: string): Realm.RegionEnum | null {
  const lower = region.toLowerCase();
  const allowed = Object.values(Realm.RegionEnum) as readonly string[];
  return allowed.includes(lower) ? (lower as Realm.RegionEnum) : null;
}

@Injectable({ providedIn: 'root' })
export class RealmSelectionService {
  readonly commodityDetails = signal<RealmDetail['commodity'] | null>(null);
  readonly auctionHouseDetails = signal<RealmDetail['auctionHouse'] | null>(null);
  private readonly realmApi = inject(RealmApiService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly realmsSignal = signal<readonly Realm[]>([]);
  readonly realms = this.realmsSignal.asReadonly();
  private readonly selectedSignal = signal<Realm | null>(null);
  readonly selected = this.selectedSignal.asReadonly();
  /** Composed from selected-realm and commodity `lastModified`; null until `getRealm` succeeds in the browser. */
  private readonly marketDataVersionSignal = signal<string | null>(null);
  /** When set, market browser may serve cached search/filter responses for this snapshot. */
  readonly marketDataVersion = this.marketDataVersionSignal.asReadonly();
  private catalogPromise: Promise<readonly Realm[]> | null = null;
  private inflightHydrate: Promise<boolean> | null = null;
  private inflightHydrateKey: string | null = null;

  constructor() {
    const stored = this.readStoredSelection();
    if (stored) {
      this.selectedSignal.set(this.placeholderRealm(stored.region, stored.slug));
    }

    if (isPlatformBrowser(this.platformId)) {
      this.router.events
        .pipe(
          filter((e): e is NavigationEnd => e instanceof NavigationEnd),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe(() => {
          const match = this.router.url.match(REALM_PATH);
          if (!match) return;
          const [, region, slug] = match;
          void this.hydrateSelectedFromApi(region, slug);
        });
    }
  }

  /**
   * Full realm list — only needed on the realm-picker page. Idempotent.
   */
  ensureCatalogLoaded(): Promise<readonly Realm[]> {
    if (!this.catalogPromise) {
      this.catalogPromise = firstValueFrom(this.realmApi.listRealms()).then((realms) => {
        const list = realms ?? [];
        this.realmsSignal.set(list);
        const current = this.selectedSignal();
        if (current) {
          const hydrated = this.findIn(list, current.region, current.slug);
          if (hydrated) {
            this.selectedSignal.set(hydrated);
            this.marketDataVersionSignal.set(null);
          } else {
            this.clearStoredSelection();
            this.selectedSignal.set(null);
          }
        }
        return list;
      });
    }
    return this.catalogPromise;
  }

  /**
   * Validates the URL against the API and replaces the selection with the full {@link Realm}.
   * Used by the route guard (browser) and after navigations under `/:region/:realm`.
   */
  hydrateSelectedFromApi(region: string, slug: string): Promise<boolean> {
    if (!isPlatformBrowser(this.platformId)) {
      return Promise.resolve(false);
    }
    const r = toRegionEnum(region);
    if (!r) return Promise.resolve(false);

    const key = `${r}:${slug.toLowerCase()}`;
    if (this.inflightHydrate && this.inflightHydrateKey === key) {
      return this.inflightHydrate;
    }

    const promise = firstValueFrom(this.realmApi.getRealm(r, slug))
      .then((detail) => {
        this.selectedSignal.set(detail.realm);
        this.commodityDetails.set(detail.commodity);
        this.auctionHouseDetails.set(detail.auctionHouse);
        this.persistSelection({ region: detail.realm.region, slug: detail.realm.slug });
        this.marketDataVersionSignal.set(composeMarketDataVersion(detail));
        return true;
      })
      .catch(() => false);

    this.inflightHydrate = promise;
    this.inflightHydrateKey = key;
    return promise.finally(() => {
      if (this.inflightHydrateKey === key && this.inflightHydrate === promise) {
        this.inflightHydrate = null;
        this.inflightHydrateKey = null;
      }
    });
  }

  hydrateStoredSelectionFromApi(): Promise<boolean> {
    const selected = this.selectedSignal();
    if (!selected || selected.locale) {
      return Promise.resolve(Boolean(selected));
    }
    return this.hydrateSelectedFromApi(selected.region, selected.slug);
  }

  /**
   * SSR-only: set selection from URL so shell/menu can render without calling the API.
   */
  selectPlaceholderFromUrl(region: string, slug: string): void {
    const r = toRegionEnum(region);
    if (!r) return;
    this.selectedSignal.set(this.placeholderRealm(r, slug));
    this.marketDataVersionSignal.set(null);
  }

  findBy(region: string, slug: string): Realm | null {
    return this.findIn(this.realmsSignal(), region, slug);
  }

  select(realm: Realm): void {
    this.selectedSignal.set(realm);
    this.persistSelection({ region: realm.region, slug: realm.slug });
    this.marketDataVersionSignal.set(null);
  }

  clear(): void {
    this.selectedSignal.set(null);
    this.clearStoredSelection();
    this.marketDataVersionSignal.set(null);
  }

  private placeholderRealm(region: Realm.RegionEnum, slug: string): Realm {
    return {
      region,
      slug,
      name: slug,
      category: '',
      locale: '',
      timezone: '',
    };
  }

  private findIn(list: readonly Realm[], region: string, slug: string): Realm | null {
    const normalizedRegion = region.toLowerCase();
    const normalizedSlug = slug.toLowerCase();
    return (
      list.find(
        (realm) =>
          realm.region.toLowerCase() === normalizedRegion &&
          realm.slug.toLowerCase() === normalizedSlug,
      ) ?? null
    );
  }

  private readStoredSelection(): PersistedSelection | null {
    if (!isPlatformBrowser(this.platformId)) return null;
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as Partial<PersistedSelection>;
      if (!parsed.region || !parsed.slug) return null;
      const allowedRegions = Object.values(Realm.RegionEnum) as readonly string[];
      if (!allowedRegions.includes(parsed.region)) return null;
      return { region: parsed.region, slug: parsed.slug };
    } catch {
      return null;
    }
  }

  private persistSelection(selection: PersistedSelection): void {
    if (!isPlatformBrowser(this.platformId)) return;
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(selection));
    } catch {
      // Storage may be disabled in private browsing; selection still lives in the signal.
    }
  }

  private clearStoredSelection(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    try {
      window.localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Same caveat as above.
    }
  }
}
