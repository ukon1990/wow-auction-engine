import { DOCUMENT } from '@angular/common';
import { LOCALE_ID, PLATFORM_ID, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { RealmSelectionService } from './realm-selection.service';
import { LocaleService, selectedRealmFormatLocale } from './locale.service';

describe('LocaleService', () => {
  function createService(realmLocale: string | null | undefined, appLocale = 'de') {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        LocaleService,
        { provide: LOCALE_ID, useValue: appLocale },
        { provide: PLATFORM_ID, useValue: 'server' },
        { provide: DOCUMENT, useValue: { location: { pathname: '/' }, cookie: '' } },
        {
          provide: RealmSelectionService,
          useValue: {
            selected: signal(realmLocale == null ? null : { locale: realmLocale }),
          },
        },
      ],
    });

    return TestBed.inject(LocaleService);
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('uses the selected realm locale for formatting', () => {
    expect(createService('en_GB').formatLocale()).toBe('en-GB');
    expect(createService('en_US').formatLocale()).toBe('en-US');
  });

  it('falls back to the active app locale when no realm locale is selected', () => {
    expect(createService(null, 'fr').formatLocale()).toBe('fr');
  });

  it('provides the selected realm locale for Angular pipes', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: DOCUMENT, useValue: { location: { pathname: '/fr/profile' } } },
        {
          provide: RealmSelectionService,
          useValue: {
            selected: signal({ locale: 'en_GB' }),
          },
        },
      ],
    });

    expect(TestBed.runInInjectionContext(() => selectedRealmFormatLocale())).toBe('en-GB');
  });

  it('falls back to the path locale for Angular pipes when no realm is selected', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: DOCUMENT, useValue: { location: { pathname: '/fr/profile' } } },
        {
          provide: RealmSelectionService,
          useValue: {
            selected: signal(null),
          },
        },
      ],
    });

    expect(TestBed.runInInjectionContext(() => selectedRealmFormatLocale())).toBe('fr');
  });
});
