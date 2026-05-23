import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { Realm, RealmApiService } from '@api/generated';
import { RealmSelectionService } from './realm-selection.service';

const realmFixture: Realm = {
  region: Realm.RegionEnum.Eu,
  name: 'Stormrage',
  slug: 'stormrage',
  category: 'PvP',
  locale: 'en_GB',
  timezone: 'Europe/Paris',
};

const otherRealm: Realm = {
  region: Realm.RegionEnum.Us,
  name: 'Illidan',
  slug: 'illidan',
  category: 'PvE',
  locale: 'en_US',
  timezone: 'America/Chicago',
};

const ahStub = {
  connectedRealmId: 1,
  lastDailyPriceUpdate: null,
  lastModified: null,
  nextUpdate: null,
};

const detailFixture = {
  realm: realmFixture,
  auctionHouse: ahStub,
  commodity: { ...ahStub, connectedRealmId: -2 },
};

type RealmApiTestStub = {
  listRealms?: () => Observable<Realm[]>;
  getRealm?: (region: string, slug: string) => Observable<unknown>;
};

function createService(api: RealmApiTestStub): RealmSelectionService {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: RealmApiService,
        useValue: {
          listRealms: () => of([]),
          getRealm: () => throwError(() => new Error('getRealm not mocked')),
          ...api,
        } as RealmApiService,
      },
    ],
  });
  return TestBed.inject(RealmSelectionService);
}

describe('RealmSelectionService', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('loads the catalog once and caches the result', async () => {
    const listRealms = vitest.fn().mockReturnValue(of([realmFixture]));
    const service = createService({ listRealms });

    await service.ensureCatalogLoaded();
    await service.ensureCatalogLoaded();

    expect(listRealms).toHaveBeenCalledTimes(1);
    expect(service.realms()).toEqual([realmFixture]);
  });

  it('persists the selection to localStorage when select() is called', () => {
    const service = createService({ listRealms: () => of([realmFixture]) });

    service.select(realmFixture);

    expect(service.selected()?.slug).toBe('stormrage');
    expect(localStorage.getItem('wae.selectedRealm')).toBe(
      JSON.stringify({ region: 'eu', slug: 'stormrage' }),
    );
  });

  it('rehydrates the selection from localStorage on construction', () => {
    localStorage.setItem('wae.selectedRealm', JSON.stringify({ region: 'eu', slug: 'stormrage' }));

    const service = createService({ listRealms: () => of([realmFixture]) });

    expect(service.selected()?.region).toBe('eu');
    expect(service.selected()?.slug).toBe('stormrage');
  });

  it('replaces the placeholder selection with the full realm once the catalog loads', async () => {
    localStorage.setItem('wae.selectedRealm', JSON.stringify({ region: 'eu', slug: 'stormrage' }));
    const service = createService({ listRealms: () => of([realmFixture, otherRealm]) });

    await service.ensureCatalogLoaded();

    expect(service.selected()?.name).toBe('Stormrage');
    expect(service.selected()?.timezone).toBe('Europe/Paris');
  });

  it('clears a stored selection that no longer exists in the catalog', async () => {
    localStorage.setItem(
      'wae.selectedRealm',
      JSON.stringify({ region: 'eu', slug: 'ghost-realm' }),
    );
    const service = createService({ listRealms: () => of([realmFixture]) });

    await service.ensureCatalogLoaded();

    expect(service.selected()).toBeNull();
    expect(localStorage.getItem('wae.selectedRealm')).toBeNull();
  });

  it('ignores unrelated localStorage shapes', () => {
    localStorage.setItem('wae.selectedRealm', 'not-json');
    const service = createService({ listRealms: () => of([realmFixture]) });

    expect(service.selected()).toBeNull();
  });

  it('findBy is case-insensitive on region and slug', async () => {
    const service = createService({ listRealms: () => of([realmFixture]) });
    await service.ensureCatalogLoaded();

    expect(service.findBy('EU', 'StormRage')?.slug).toBe('stormrage');
    expect(service.findBy('eu', 'unknown')).toBeNull();
  });

  it('clear() removes selection in memory and storage', () => {
    const service = createService({ listRealms: () => of([realmFixture]) });
    service.select(realmFixture);

    service.clear();

    expect(service.selected()).toBeNull();
    expect(localStorage.getItem('wae.selectedRealm')).toBeNull();
  });

  it('propagates catalog load errors so callers can react', async () => {
    const service = createService({
      listRealms: () => throwError(() => new Error('boom')),
    });

    await expect(service.ensureCatalogLoaded()).rejects.toThrow('boom');
  });

  it('hydrateSelectedFromApi fetches detail and selects the full realm', async () => {
    const getRealm = vitest.fn().mockReturnValue(of(detailFixture));
    const service = createService({ getRealm });

    const ok = await service.hydrateSelectedFromApi('eu', 'stormrage');

    expect(ok).toBe(true);
    expect(getRealm).toHaveBeenCalledWith('eu', 'stormrage');
    expect(service.selected()?.name).toBe('Stormrage');
    expect(localStorage.getItem('wae.selectedRealm')).toBe(
      JSON.stringify({ region: 'eu', slug: 'stormrage' }),
    );
  });

  it('hydrates a stored placeholder selection from the API', async () => {
    localStorage.setItem('wae.selectedRealm', JSON.stringify({ region: 'eu', slug: 'stormrage' }));
    const getRealm = vitest.fn().mockReturnValue(of(detailFixture));
    const service = createService({ getRealm });

    const ok = await service.hydrateStoredSelectionFromApi();

    expect(ok).toBe(true);
    expect(getRealm).toHaveBeenCalledWith('eu', 'stormrage');
    expect(service.selected()?.locale).toBe('en_GB');
  });

  it('does not refetch a selected realm that already has locale data', async () => {
    const getRealm = vitest.fn();
    const service = createService({ getRealm });
    service.select(realmFixture);

    const ok = await service.hydrateStoredSelectionFromApi();

    expect(ok).toBe(true);
    expect(getRealm).not.toHaveBeenCalled();
  });

  it('hydrateSelectedFromApi returns false when the API fails', async () => {
    const service = createService({
      getRealm: () => throwError(() => new Error('404')),
    });

    const ok = await service.hydrateSelectedFromApi('eu', 'nope');

    expect(ok).toBe(false);
  });

  it('hydrateSelectedFromApi returns false for an unknown region code', async () => {
    const getRealm = vitest.fn();
    const service = createService({ getRealm });

    const ok = await service.hydrateSelectedFromApi('zz', 'stormrage');

    expect(ok).toBe(false);
    expect(getRealm).not.toHaveBeenCalled();
  });
});
