import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import { Realm, RealmApiService } from '../../api/generated';
import { RealmSelectionService } from '../../core/services/realm-selection.service';
import { SelectRealmPage } from './select-realm.page';

const realmsFixture: readonly Realm[] = [
  {
    region: Realm.RegionEnum.Eu,
    name: 'Stormrage',
    slug: 'stormrage',
    category: 'PvP',
    locale: 'en_GB',
    timezone: 'Europe/Paris',
  },
  {
    region: Realm.RegionEnum.Eu,
    name: 'Aerie Peak',
    slug: 'aerie-peak',
    category: 'PvE',
    locale: 'en_GB',
    timezone: 'Europe/Paris',
  },
  {
    region: Realm.RegionEnum.Us,
    name: 'Illidan',
    slug: 'illidan',
    category: 'PvE',
    locale: 'en_US',
    timezone: 'America/Chicago',
  },
];

describe('SelectRealmPage', () => {
  let fixture: ComponentFixture<SelectRealmPage>;
  let component: SelectRealmPage;
  let realmApi: {
    listRealms: ReturnType<typeof vitest.fn>;
    getRealm: ReturnType<typeof vitest.fn>;
  };

  beforeEach(async () => {
    realmApi = {
      listRealms: vitest.fn().mockReturnValue(of(realmsFixture)),
      getRealm: vitest.fn().mockReturnValue(
        of({
          realm: realmsFixture[0],
          auctionHouse: { connectedRealmId: 1 },
          community: { connectedRealmId: -2 },
        }),
      ),
    };

    localStorage.clear();

    await TestBed.configureTestingModule({
      imports: [SelectRealmPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: RealmApiService, useValue: realmApi },
      ],
    }).compileComponents();

    await TestBed.inject(RealmSelectionService).ensureCatalogLoaded();
    fixture = TestBed.createComponent(SelectRealmPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('renders all realms after the API resolves', () => {
    const buttons = fixture.nativeElement.querySelectorAll('li button');
    expect(buttons.length).toBe(3);
  });

  it('filters realms by name, slug, or region (case-insensitive)', async () => {
    component['onQueryChanged']('storm');
    fixture.detectChanges();
    let buttons = fixture.nativeElement.querySelectorAll('li button');
    expect(buttons.length).toBe(1);
    expect(buttons[0].textContent).toContain('Stormrage');

    component['onQueryChanged']('US');
    fixture.detectChanges();
    buttons = fixture.nativeElement.querySelectorAll('li button');
    expect(buttons.length).toBe(1);
    expect(buttons[0].textContent).toContain('Illidan');
  });

  it('navigates to /:region/:slug and persists the selection when a realm is chosen', () => {
    const router = TestBed.inject(Router);
    const navigate = vitest.spyOn(router, 'navigate').mockResolvedValue(true);

    component['select'](realmsFixture[0]);

    expect(navigate).toHaveBeenCalledWith(['/', 'eu', 'stormrage']);
    expect(localStorage.getItem('wae.selectedRealm')).toBe(
      JSON.stringify({ region: 'eu', slug: 'stormrage' }),
    );
  });
});
