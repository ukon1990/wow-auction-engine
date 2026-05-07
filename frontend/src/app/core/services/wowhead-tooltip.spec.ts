import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Realm } from '@api/generated';
import { RealmSelectionService } from './realm-selection.service';
import { WowheadTooltipService } from './wowhead-tooltip';

describe('WowheadTooltipService', () => {
  let service: WowheadTooltipService;
  let httpMock: HttpTestingController;
  const routerEvents = new Subject<unknown>();

  const realm: Realm = {
    region: Realm.RegionEnum.Eu,
    name: 'R',
    slug: 'r',
    category: 'c',
    locale: 'de_DE',
    timezone: 't',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        WowheadTooltipService,
        {
          provide: RealmSelectionService,
          useValue: {
            selected: () => realm,
          },
        },
        { provide: Router, useValue: { events: routerEvents.asObservable() } },
      ],
    });
    service = TestBed.inject(WowheadTooltipService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    service.clear();
  });

  it('fetches tooltip and sets active overlay', async () => {
    const promise = service.show({
      wowheadType: 'item',
      id: 1,
      isClassic: false,
      event: new MouseEvent('mouseenter', { clientX: 10, clientY: 20 }),
      describedById: 'tip-1',
    });

    const req = httpMock.expectOne(
      (r) =>
        r.url.startsWith('https://nether.wowhead.com/tooltip/item/1') &&
        r.url.includes('locale=en'),
    );
    req.flush({ tooltip: '<b>Hello</b>' });

    await promise;

    const active = service.active();
    expect(active?.describedById).toBe('tip-1');
    expect(active?.leftPx).toBe(40);
    expect(active?.topPx).toBe(20);
  });

  it('reuses cache for the same URL', async () => {
    const ev = new MouseEvent('mouseenter', { clientX: 0, clientY: 0 });

    const first = service.show({
      wowheadType: 'item',
      id: 99,
      isClassic: false,
      event: ev,
      describedById: 'a',
    });
    httpMock.expectOne((r) => r.url.includes('/tooltip/item/99')).flush({ tooltip: 'x' });
    await first;

    service.clear();

    await service.show({
      wowheadType: 'item',
      id: 99,
      isClassic: false,
      event: ev,
      describedById: 'b',
    });

    expect(httpMock.match(() => true).length).toBe(0);
    expect(service.active()?.describedById).toBe('b');
  });

  it('does not resurrect the overlay after clear while the request is in flight', async () => {
    const promise = service.show({
      wowheadType: 'item',
      id: 1,
      isClassic: false,
      event: new MouseEvent('mouseenter', { clientX: 10, clientY: 20 }),
      describedById: 'tip-1',
    });

    const req = httpMock.expectOne((r) => r.url.includes('/tooltip/item/1'));
    service.clear();
    req.flush({ tooltip: '<b>late</b>' });

    await promise;

    expect(service.active()).toBeNull();
  });

  it('clears the overlay automatically after 10 seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const promise = service.show({
        wowheadType: 'item',
        id: 1,
        isClassic: false,
        event: new MouseEvent('mouseenter', { clientX: 10, clientY: 20 }),
        describedById: 'tip-1',
      });

      const req = httpMock.expectOne((r) => r.url.includes('/tooltip/item/1'));
      req.flush({ tooltip: '<b>Hello</b>' });
      await promise;

      expect(service.active()).not.toBeNull();

      vi.advanceTimersByTime(10_000);
      expect(service.active()).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});
