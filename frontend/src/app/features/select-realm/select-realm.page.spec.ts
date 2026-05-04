import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Realm, RealmApiService } from '@api/generated';
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
          commodity: { connectedRealmId: -2 },
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
    const links = fixture.nativeElement.querySelectorAll('li a');
    expect(links.length).toBe(3);
    expect(links[0].getAttribute('href')).toContain('/eu/stormrage');
  });

  it('filters realms by name, slug, or region (case-insensitive)', async () => {
    component['onQueryChanged']('storm');
    fixture.detectChanges();
    let links = fixture.nativeElement.querySelectorAll('li a');
    expect(links.length).toBe(1);
    expect(links[0].textContent).toContain('Stormrage');

    component['onQueryChanged']('US');
    fixture.detectChanges();
    links = fixture.nativeElement.querySelectorAll('li a');
    expect(links.length).toBe(1);
    expect(links[0].textContent).toContain('Illidan');
  });

  it('persists the selection when a realm link is activated', () => {
    component['rememberSelection'](realmsFixture[0]);

    expect(localStorage.getItem('wae.selectedRealm')).toBe(
      JSON.stringify({ region: 'eu', slug: 'stormrage' }),
    );
  });
});
