import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot } from '@angular/router';
import { of } from 'rxjs';

import { Realm, RealmApiService } from '@api/generated';
import { MenuService } from './menu.service';

const realmFixture: Realm = {
  region: Realm.RegionEnum.Eu,
  name: 'Stormrage',
  slug: 'stormrage',
  category: 'PvP',
  locale: 'en_GB',
  timezone: 'Europe/Paris',
};

describe('MenuService', () => {
  let service: MenuService;
  let routeSnapshot: ActivatedRouteSnapshot;
  let routerState: RouterStateSnapshot;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: RealmApiService,
          useValue: {
            listRealms: () => of([]),
            getRealm: () =>
              of({
                realm: realmFixture,
                auctionHouse: { connectedRealmId: 1 },
                commodity: { connectedRealmId: -2 },
              }),
          },
        },
      ],
    });
    service = TestBed.inject(MenuService);
    routeSnapshot = {} as ActivatedRouteSnapshot;
    routerState = { url: '/test' } as RouterStateSnapshot;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getActiveRouteLinks', () => {
    it('returns nav items for routes with a title and icon, exposing absolute routerLinks', async () => {
      const links = await service.getActiveRouteLinks(
        [
          { title: 'First page', path: '', icon: 'home' },
          { title: 'Second page', path: 'second', icon: 'pageview' },
        ],
        routeSnapshot,
        routerState,
      );

      expect(links.length).toBe(2);
      expect(links[0].label).toBe('First page');
      expect(links[0].icon).toBe('home');
      expect(links[0].routerLink).toBe('/');
      expect(links[1].routerLink).toBe('/second');
      expect(links[0].children).toEqual([]);
    });

    it('includes link when canActivate returns true', async () => {
      const canActivate = vitest.fn().mockReturnValue(Promise.resolve(true));
      const links = await service.getActiveRouteLinks(
        [
          {
            title: 'Admin page',
            path: 'admin',
            icon: 'admin_panel_settings',
            canActivate: [canActivate],
          },
        ],
        routeSnapshot,
        routerState,
      );

      expect(links.length).toBe(1);
    });

    it('hides links if the user can not activate them', async () => {
      const canActivate = vitest.fn().mockReturnValue(Promise.resolve(false));
      const links = await service.getActiveRouteLinks(
        [
          {
            title: 'Admin page',
            path: 'admin',
            icon: 'admin_panel_settings',
            canActivate: [canActivate],
          },
        ],
        routeSnapshot,
        routerState,
      );

      expect(links.length).toBe(0);
    });

    it('skips routes that need a realm when none has been selected, but still descends through realm-less parents', async () => {
      const links = await service.getActiveRouteLinks(
        [
          {
            path: ':region/:realm',
            children: [{ path: 'auctions', title: 'Auctions', icon: 'travel_explore' }],
          },
        ],
        routeSnapshot,
        routerState,
        '',
        null,
      );

      expect(links).toEqual([]);
    });

    it('substitutes :region and :realm with the selected realm to build absolute paths', async () => {
      const links = await service.getActiveRouteLinks(
        [
          {
            path: ':region/:realm',
            children: [{ path: 'auctions', title: 'Auctions', icon: 'travel_explore' }],
          },
        ],
        routeSnapshot,
        routerState,
        '',
        realmFixture,
      );

      expect(links.length).toBe(1);
      expect(links[0].label).toBe('Auctions');
      expect(links[0].routerLink).toBe('/eu/stormrage/auctions');
    });

    it('includes direct children of :region/:realm in the nav together with future top-level routes', async () => {
      const links = await service.getActiveRouteLinks(
        [
          {
            path: ':region/:realm',
            children: [
              { path: 'auctions', title: 'Auctions', icon: 'travel_explore' },
              { path: 'crafting', title: 'Crafting', icon: 'schema' },
            ],
          },
          { path: 'settings', title: 'Settings', icon: 'settings' },
        ],
        routeSnapshot,
        routerState,
        '',
        realmFixture,
      );

      expect(links.map((l) => l.label)).toEqual(['Auctions', 'Crafting', 'Settings']);
      expect(links.map((l) => l.routerLink)).toEqual([
        '/eu/stormrage/auctions',
        '/eu/stormrage/crafting',
        '/settings',
      ]);
    });

    it('shows top-level titled routes without a selected realm, but not realm-scoped children', async () => {
      const links = await service.getActiveRouteLinks(
        [
          {
            path: ':region/:realm',
            children: [{ path: 'auctions', title: 'Auctions', icon: 'travel_explore' }],
          },
          { path: 'settings', title: 'Settings', icon: 'settings' },
        ],
        routeSnapshot,
        routerState,
        '',
        null,
      );

      expect(links.length).toBe(1);
      expect(links[0].label).toBe('Settings');
      expect(links[0].routerLink).toBe('/settings');
    });
  });
});
