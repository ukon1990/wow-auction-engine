import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  convertToParamMap,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { Realm, RealmApiService } from '@api/generated';
import { realmSelectedGuard } from './realm-selected.guard';
import { RealmSelectionService } from '../services/realm-selection.service';

const realmFixture: Realm = {
  region: Realm.RegionEnum.Eu,
  name: 'Stormrage',
  slug: 'stormrage',
  category: 'PvP',
  locale: 'en_GB',
  timezone: 'Europe/Paris',
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

function createSnapshot(params: Record<string, string>): ActivatedRouteSnapshot {
  return {
    paramMap: convertToParamMap(params),
  } as ActivatedRouteSnapshot;
}

const stateStub = { url: '/test' } as RouterStateSnapshot;

async function runGuard(snapshot: ActivatedRouteSnapshot): Promise<boolean | UrlTree> {
  return TestBed.runInInjectionContext(async () =>
    (realmSelectedGuard as CanActivateFn)(snapshot, stateStub),
  ) as Promise<boolean | UrlTree>;
}

type RealmApiTestStub = {
  listRealms?: () => Observable<unknown>;
  getRealm?: (region: string, slug: string) => Observable<unknown>;
};

function setup(
  api: RealmApiTestStub,
  storedSelection?: { region: string; slug: string },
): { router: Router; selection: RealmSelectionService } {
  TestBed.resetTestingModule();
  if (storedSelection) {
    localStorage.setItem('wae.selectedRealm', JSON.stringify(storedSelection));
  }
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: RealmApiService,
        useValue: {
          listRealms: () => of([]),
          getRealm: () => of(detailFixture),
          ...api,
        } as RealmApiService,
      },
    ],
  });
  return {
    router: TestBed.inject(Router),
    selection: TestBed.inject(RealmSelectionService),
  };
}

describe('realmSelectedGuard', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('allows activation when getRealm succeeds', async () => {
    const getRealm = vitest.fn().mockReturnValue(of(detailFixture));
    setup({ getRealm });

    const result = await runGuard(createSnapshot({ region: 'eu', realm: 'stormrage' }));

    expect(result).toBe(true);
    expect(getRealm).toHaveBeenCalledWith('eu', 'stormrage');
    expect(localStorage.getItem('wae.selectedRealm')).toBe(
      JSON.stringify({ region: 'eu', slug: 'stormrage' }),
    );
  });

  it('redirects to / when getRealm fails', async () => {
    const { router } = setup({
      getRealm: () => throwError(() => new Error('network')),
    });
    const expected = router.createUrlTree(['/']);

    const result = await runGuard(createSnapshot({ region: 'eu', realm: 'ghost' }));

    expect(result).toEqual(expected);
  });

  it('redirects to the stored realm when the URL has no params', async () => {
    const { router } = setup({}, { region: 'eu', slug: 'stormrage' });
    const expected = router.createUrlTree(['/', 'eu', 'stormrage']);

    const result = await runGuard(createSnapshot({}));

    expect(result).toEqual(expected);
  });

  it('redirects to / when neither URL params nor a stored selection are present', async () => {
    const { router } = setup({});
    const expected = router.createUrlTree(['/']);

    const result = await runGuard(createSnapshot({}));

    expect(result).toEqual(expected);
  });
});
