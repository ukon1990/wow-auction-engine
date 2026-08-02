import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';

import { Realm } from '@api/generated';
import { RealmSelectionService } from '../services/realm-selection.service';
import { WowheadTooltipService } from '../services/wowhead-tooltip';
import { WowheadItemTooltipDirective } from './wowhead-item-tooltip';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WowheadItemTooltipDirective],
  template: `<span appWowheadItemTooltip [itemId]="42">Item</span>`,
})
class WowheadHostComponent {}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WowheadItemTooltipDirective],
  template: `<span appWowheadItemTooltip [itemId]="42" [bonusKey]="'6652:7'">Item</span>`,
})
class WowheadBonusKeyHostComponent {}

describe('WowheadItemTooltipDirective', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [WowheadHostComponent],
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
    });
  });

  it('requests a tooltip on mouseenter', async () => {
    const fixture = TestBed.createComponent(WowheadHostComponent);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    const span = (fixture.nativeElement as HTMLElement).querySelector('span')!;
    span.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true, clientX: 5, clientY: 6 }));

    const request = httpMock.expectOne((httpRequest) =>
      httpRequest.url.includes('/tooltip/item/42'),
    );
    request.flush({ tooltip: '<span>Tip</span>' });

    await fixture.whenStable();
    expect(TestBed.inject(WowheadTooltipService).active()).not.toBeNull();
    httpMock.verify();
  });

  it('appends bonus query from bonusKey when bonusIds empty', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [WowheadBonusKeyHostComponent],
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
    });
    const fixture = TestBed.createComponent(WowheadBonusKeyHostComponent);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    const span = (fixture.nativeElement as HTMLElement).querySelector('span')!;
    span.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true, clientX: 5, clientY: 6 }));

    const request = httpMock.expectOne(
      (httpRequest) =>
        httpRequest.url.includes('/tooltip/item/42') && httpRequest.url.includes('bonus=6652:7'),
    );
    request.flush({ tooltip: '<span>Tip</span>' });

    await fixture.whenStable();
    expect(TestBed.inject(WowheadTooltipService).active()).not.toBeNull();
    httpMock.verify();
  });
});
