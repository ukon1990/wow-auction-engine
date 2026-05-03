import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { App } from './app';
import { Realm, RealmApiService } from './api/generated';

const apiStubs = {
  listRealms: () => of([]),
  getRealm: () =>
    of({
      realm: {
        region: Realm.RegionEnum.Eu,
        name: 'R',
        slug: 'r',
        category: '',
        locale: '',
        timezone: '',
      },
      auctionHouse: { connectedRealmId: 1 },
      community: { connectedRealmId: -2 },
    }),
};

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: RealmApiService, useValue: apiStubs }],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
