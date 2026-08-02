import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';

import { Realm } from '@api/generated';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { WowheadTooltipService } from '@core/services/wowhead-tooltip';
import { WowheadTooltipLayer } from './wowhead-tooltip-layer';

describe('WowheadTooltipLayer', () => {
  let fixture: ComponentFixture<WowheadTooltipLayer>;
  let httpMock: HttpTestingController;
  let tooltips: WowheadTooltipService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WowheadTooltipLayer],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        WowheadTooltipService,
        {
          provide: RealmSelectionService,
          useValue: {
            selected: (): Realm => ({
              region: Realm.RegionEnum.Us,
              name: 'x',
              slug: 'x',
              category: 'c',
              locale: 'en_US',
              timezone: 't',
            }),
          },
        },
        { provide: Router, useValue: { events: new Subject().asObservable() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WowheadTooltipLayer);
    httpMock = TestBed.inject(HttpTestingController);
    tooltips = TestBed.inject(WowheadTooltipService);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders role="tooltip" when the service has active HTML', async () => {
    const showPromise = tooltips.show({
      wowheadType: 'item',
      id: 7,
      isClassic: false,
      event: new MouseEvent('mousemove', { clientX: 1, clientY: 2 }),
      describedById: 'tid',
    });
    httpMock
      .expectOne((request) => request.url.includes('/tooltip/item/7'))
      .flush({ tooltip: '<b>x</b>' });
    await showPromise;

    fixture.detectChanges();
    const tooltipElement = (fixture.nativeElement as HTMLElement).querySelector('[role="tooltip"]');
    expect(tooltipElement).toBeTruthy();
    expect(tooltipElement?.id).toBe('tid');
  });

  it('renders the app current buyout when supplied', async () => {
    const showPromise = tooltips.show({
      wowheadType: 'item',
      id: 7,
      isClassic: false,
      currentBuyout: { gold: 1234, silver: 56, copper: 78 },
      event: new MouseEvent('mousemove', { clientX: 1, clientY: 2 }),
      describedById: 'tid',
    });
    httpMock
      .expectOne((request) => request.url.includes('/tooltip/item/7'))
      .flush({ tooltip: '<b>x</b>' });
    await showPromise;

    fixture.detectChanges();
    const tooltipElement = (fixture.nativeElement as HTMLElement).querySelector('[role="tooltip"]');
    expect(tooltipElement?.textContent).toContain('Current Buyout: 1,2345678');
    expect(tooltipElement?.querySelector('.whtt-current-buyout .moneygold')?.textContent).toBe(
      '1,234',
    );
    expect(tooltipElement?.querySelector('.whtt-current-buyout .moneysilver')?.textContent).toBe(
      '56',
    );
    expect(tooltipElement?.querySelector('.whtt-current-buyout .moneycopper')?.textContent).toBe(
      '78',
    );
  });
});
